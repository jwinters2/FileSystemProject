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

  public synchronized FileTableEntry fretrieve(String filename, String mode)
  {
    short inum = dir.iretrieve(filename,mode);
    // if we get -1, there is no such entry (and we can't make one)
    if(inum == -1)
    {
      return null;
    }

    // search for it
    for(int i=0; i<table.size(); i++)
    {
      if(table.get(i).iNumber == inum) 
      {
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

    // public methods
    public synchronized FileTableEntry falloc( String filename, String mode ){
      // allocate a new file structure table entry for this file name

      short inum  = dir.iretrieve(filename,mode);
      Inode inode = Inode.getInode(inum);

      FileTableEntry entry = new FileTableEntry(inode,inum,mode);
      // allocate/retrive and register the correspoding inode using dir
      //increment this inode count
      entry.count++;

      //immidiately write back this inode to disk

      entry.inode.toDisk(entry.iNumber);

      table.add(entry);
      entry.fileDescriptor = table.size() - 1;

      return entry;
     }

    public synchronized boolean ffree( FileTableEntry entry )
    {
      //receive a file table entry reference
      // save the corresopding inode to disk
       if(table.indexOf(entry) == -1)
       {
         return false;
       }

       entry.inode.toDisk(entry.iNumber);
       // free this file table entry
       entry.count--;
       if(entry.count == 0)
       {
         table.remove(entry);
       }
       return true;
    }

    public synchronized boolean fempty() {
        return table.isEmpty();

    }

}
