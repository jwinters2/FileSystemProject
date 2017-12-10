public class Superblock
{
  public int totalBlocks;
  public int totalInodes;
  public int freeList;

  public Superblock(int diskSize)
  {
    // if a valid superblock is in memory (total blocks is not -1)
    // load the data straight from the disc
    // otherwise make an empty superblock

    byte[] blockData = new byte[Disk.blockSize];
    SysLib.cread(0,blockData);

    // invalid superblock, make a new one
    if(FileSystem.bytesToInt(0,blockData) == -1)
    {
      totalBlocks = diskSize;

      // no inodes, but 0 is taken by the superblock, so use 1
      totalInodes = 0;
      freeList    = 1;
    }
    else
    {
      totalBlocks = FileSystem.bytesToInt(0,blockData);
      totalInodes = FileSystem.bytesToInt(4,blockData);
      freeList    = FileSystem.bytesToInt(8,blockData);
    }
  }

  public void toDisk()
  {
    byte[] blockData = new byte[Disk.blockSize];
    SysLib.cread(0,blockData);

    FileSystem.intToBytes(0,  totalBlocks, blockData);
    FileSystem.intToBytes(4,  totalInodes, blockData);
    FileSystem.intToBytes(8,  freeList,    blockData);

    SysLib.cwrite(0,blockData);
  }

  public void testPrompt(String cmd,int x,int y,int z)
  {
    SysLib.cout("Superblock Test: ");

    //if(cmd.toString().toLowerCase().equals("format"))
    //{
      //SysLib.cout("Formatting Disk\n");
      //format();
    //}
    /*else*/ if(cmd.toString().toLowerCase().equals("free"))
    {
      SysLib.cout("Printing Free List\n");
      int current = freeList;
      byte[] blockData = new byte[Disk.blockSize];

      while(current != -1)
      {
        SysLib.cout(Integer.toString(current) + ","); 
        SysLib.cread(current,blockData);
        current = FileSystem.bytesToInt(0,blockData);
      }
    }
    else if(cmd.toString().toLowerCase().equals("write"))
    {
      totalBlocks = x;
      totalInodes = y;
      freeList    = z;
      SysLib.cout("Writing: " + x + ", " + y + ", " + z + "\n");
    }
    else if(cmd.toString().toLowerCase().equals("sync"))
    {
      SysLib.cout("Syncing\n");
      toDisk();
    }
    else if(cmd.toString().toLowerCase().equals("print"))
    {
      SysLib.cout("totalBlocks = " + Integer.toString(totalBlocks) + "\n");
      SysLib.cout("totalInodes = " + Integer.toString(totalInodes) + "\n");
      SysLib.cout("freeList    = " + Integer.toString(freeList   ) + "\n");
    }
  }
}
