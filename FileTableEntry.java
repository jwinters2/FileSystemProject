
public class FileTableEntry {
  public int seekPtr;
  public final Inode inode;
  public final short iNumber;
  public int count;
  public final String Mode;

  public FileTableEntry()
  {
    seekPtr = 0;
    inode = null;
    iNumber = -1;
    count = 1;
    Mode = "";
  }

  public FileTableEntry(Inode i, short in, String m)
  {
    seekPtr = 0;
    inode = i;
    iNumber = in;
    count = 1;
    Mode = m;
    if(Mode.equals("a")) // if  mode is append ,
    {
      seekPtr = inode.length;   // seekPtr points to the end of the file
    }
  }
}
