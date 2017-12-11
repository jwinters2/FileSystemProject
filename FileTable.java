import java.util.Vector;

/*
 * FileTable
 *
 * this is the "master list" for each thread's TCB file list, as in this
 * set is the union of all thread's set
 *
 * it only stores currently open files
 */
public class FileTable
{
  private Vector<FileTableEntry> table; // the actual entity of this table
  private Directory dir;                // the root directory

  /*
   * constructor
   *
   * @param : directory : Directory : the directory we're using
   *                                  normally this is just the root directory
   *                                  but in this case it's the only one
   */
  public FileTable(Directory directory)
  {
    // instantiates the file structure table
    table = new Vector<FileTableEntry>(); 

    // receives a reference from the directory
    dir = directory;              
  }

  /*
   * clear
   *
   * @return : void
   *
   * clears the table
   */
  public synchronized void clear()
  {
    table.clear();
  }

  /*
   * fretrieve (file retrieve)
   *
   * @param  : filename : String : the filename to retrieve
   * @param  : mode : String : the mode to (maybe) open the file with
   * @return : FileTableEntry : the file entry associated with this file
   *
   * retrieves a file entry from the table
   */
  public synchronized FileTableEntry fretrieve(String filename, String mode)
  {
    // get the inum for this file
    short inum = dir.iretrieve(filename,mode);

    // if we get -1, there is no such entry (and we couldn't make one)
    if(inum == -1)
    {
      SysLib.cout("iretrieve gives back -1\n");
      return null;
    }

    // search for it
    for(int i=0; i<table.size(); i++)
    {
      if(table.get(i).iNumber == inum) 
      {
        // get the entry from the table
        FileTableEntry retval = table.get(i);
        // a new thread is accessing it, so increment the count
        retval.count++;

        // don't allow for deleted files to be returned
        if(retval.inode.flag == Inode.FLAG_DELETED)
        {
          return null;
        }

        // set the seek pointer correctly
        if(mode.equals("a"))
        {
          retval.seekPtr = retval.inode.length;
        }
        else
        {
          retval.seekPtr = 0;
        }

        // return it
        return retval;
      }
    }

    // make a new entry since it wasn't found
    return falloc(filename,mode);
  }

  /*
   * indexOf
   *
   * @param  : f : FileTableEntry : the entry to get the index of
   * @return : int : the index of f
   *
   * this is just a wrapper for table.indexOf()
   */
  public synchronized int indexOf(FileTableEntry f)
  {
    return table.indexOf(f);
  }

  /*
   * getByInum
   *
   * @param  : inum : int : the inode numberto search for
   * @return : FileTableEntry : the entry with the inode num we're looking for
   *
   * finds the FileTableEntry with a specific inode number and returns it
   */
  public synchronized FileTableEntry getByInum(int inum)
  {
    for(int i=0; i<table.size(); i++)
    {
      if(table.get(i).iNumber == inum)
      {
        return table.get(i);
      }
    }
    return null;
  }

  /*
   * falloc (file allocate)
   *
   * @param  : filename : String : the filename to allocate with
   * @param  : mode : String : the mode to open the file with
   * @return : FileTableEntry : the file entry associated with the new file
   *
   * allocates a new file table entry and returns it
   */
  public synchronized FileTableEntry falloc( String filename, String mode )
  {
    // return nothing if we can't retrieve the inode
    // (we can't open one or make a new one with the mode)
    short inum  = dir.iretrieve(filename,mode);
    if(inum == -1)
    {
      return null;
    }

    // get the actual inode from the number
    Inode inode = Inode.getInode(inum);

    // make a new entry
    FileTableEntry entry = new FileTableEntry(inode,inum,mode);

    // actually add it to the table
    table.add(entry);

    return entry;
  }

  /*
   * ffree (file free)
   *
   * @param  : entry : FileTableEntry : the entry to free
   * @return : boolean : whether or not it worked
   *
   * frees a file from the list if no more threads are using it
   */
  public synchronized boolean ffree( FileTableEntry entry )
  {
    // it should fail if entry is null or it's not in the table
    if(entry == null || table.indexOf(entry) == -1)
    {
      return false;
    }

    // save the corresponding inode to disk
    entry.inode.toDisk(entry.iNumber);

    // a thread stopped using this, so decrement the count
    entry.count--;

    // if ALL threads stopped using this, remove it entirely
    if(entry.count <= 0)
    {
      table.remove(entry);
      
      // if it's been set to delete, delete it
      if(entry.inode.flag == Inode.FLAG_DELETED)
      {
        Inode.deleteInode(entry.iNumber);
      }
    }
    return true;
  }

  /*
   * isEmpty
   *
   * @return : boolean : whether or not the table is empty
   *
   * fempty returns whether or not the table is empty
   */
  public synchronized boolean isEmpty()
  {
    return table.isEmpty();
  }
}
