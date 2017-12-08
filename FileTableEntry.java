
public class FileTableEntry {
  public int seekPtr;
  public final Inode inode ;
  public final short iNumber;
  public int count;
  public final String Mode;
public FileTableEntry( Inode i, short inumber,String m){
  seekPtr =0;
  inode= i ;
  iNUmber= inumber;
  count =1 ;
  mode = m;
  if (mode.compareTo( "a") == 0  ) // if  mode is append ,
      seekPtr =inode.length;   // seekPtr points to the end of the file
  }
}
