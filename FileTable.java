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
    // public methods
    public synchronized FileTableEntry falloc( String filename, String mode ){
      // allocate a new file structure table entry for this file name

       FileTableEntry entry = new FileTableEntry() ;
       // allocate/retrive and register the correspoding inode using dir
       entry.inumber = dir.iretrieve(filename,mode);
       //increment this inode count
       entry.count++;

       //immidiately write back this inode to disk


       Inode.toDisk(entry.iNumber);

       table.add(entry);

       return entry;
     }

    public synchronized boolean ffree( FileTableEntry e ) {
      //receive a file table entry reference
      // save the corresopding inode to disk
      if(table.indexOf(e) == -1)
      {
        return false;
      }

       Inode.toDisk(e.inumber);
       // free this file table entry
       e.count--;
       if(e.count == 0)
       {
         table.remove(e);
       }
       return true;
    }

    public synchronized boolean fempty() {
        return table.isEmpty();

    }

}
