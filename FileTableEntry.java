/*
 * FileTableEntry
 *
 * a single entry for the FileTable (which stores open files), and for TCBs
 * (which I didn't write)
 */
public class FileTableEntry
{
  public int seekPtr;           // where in the file reading and writing is
  public final Inode inode;     // the inode associated with the file
  public final short iNumber;   // the inode number for this file
  public int count;             // the number of threads this file is open in
  public final String Mode;     // the mode the file is open in
                                // could be r  (read)
                                //          w  (write)
                                //          w+ (read and write)
                                //          a  (append, start at the end)

  /*
   * constructor
   *
   * this is the default constructor, sets a completely blank constructor
   */
  public FileTableEntry()
  {
    seekPtr = 0;
    inode = null;
    iNumber = -1;
    count = 1;
    Mode = "";
  }

  /*
   * constructor
   *
   * @param : i : Inode : the inode associated with this file
   * @param : in : int : the inode number associated with this file
   * @param : m : String : the mode for this entry
   */
  public FileTableEntry(Inode i, short in, String m)
  {
    seekPtr = 0;
    inode = i;
    iNumber = in;
    count = 1;
    Mode = m;

    // if mode is append ...
    if(Mode.equals("a")) 
    {
      // ... seekPtr points to the end of the file
      seekPtr = inode.length;
    }
  }
}
