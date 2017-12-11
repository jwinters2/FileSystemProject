import java.util.Vector;

public class FileTable
{
  private Vector<FileTableEntry> table;       // the actual entity of this table
  private Directory dir;      //  the root Directory

  public FileTable(Directory directory)
  {      // constructor
        table = new Vector<FileTableEntry>(); // instantiates the file structure
                                              //table
        dir = directory;              // receives a reference from the directory
  }

  public synchronized void clear()
  {
    table.clear();
  }

  public synchronized FileTableEntry fretrieve(String filename, String mode)
  {
    short inum = dir.iretrieve(filename,mode);
    // if we get -1, there is no such entry (and we can't make one)
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
        FileTableEntry retval = table.get(i);
        retval.count++;
        if(retval.inode.flag == Inode.FLAG_DELETED)
        {
          // don't allow for deleted files to be returned
          return null;
        }

        if(mode.equals("a"))
        {
          retval.seekPtr = retval.inode.length;
        }
        else
        {
          retval.seekPtr = 0;
        }
        return table.get(i);
      }
    }

    // make a new entry since it wasn't found
    return falloc(filename,mode);
  }

  public synchronized int indexOf(FileTableEntry f)
  {
    return table.indexOf(f);
  }

  public synchronized FileTableEntry getEntry(int fd)
  {
    if(fd >= 0 && fd < table.size())
    {
      return table.get(fd);
    }
    return null;
  }

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

    // public methods
    public synchronized FileTableEntry falloc( String filename, String mode ){
      // allocate a new file structure table entry for this file name

      short inum  = dir.iretrieve(filename,mode);
      if(inum == -1)
      {
        return null;
      }

      Inode inode = Inode.getInode(inum);

      FileTableEntry entry = new FileTableEntry(inode,inum,mode);
      // allocate/retrive and register the correspoding inode using dir

      //immidiately write back this inode to disk

      entry.inode.toDisk(entry.iNumber);

      table.add(entry);

      return entry;
     }

    public synchronized boolean ffree( FileTableEntry entry )
    {
      //receive a file table entry reference
      // save the corresponding inode to disk
        if(entry == null || table.indexOf(entry) == -1)
        {
          return false;
        }

        entry.inode.toDisk(entry.iNumber);
        // free this file table entry
        entry.count--;
        if(entry.count <= 0)
        {
          table.remove(entry);
          if(entry.inode.flag == Inode.FLAG_DELETED)
          {
            Inode.deleteInode(entry.iNumber);
          }
        }
        return true;
    }

    public synchronized boolean fempty() {
        return table.isEmpty();

    }

}
