public class FileSystem
{
  private static Superblock superblock;
  private static Directory directory;
  
  public FileSystem(int diskBlocks)
  {
    superblock = new Superblock(diskBlocks);
    directory = new Directory(superblock.totalInodes);

    //InodeTest();
  }

  //TODO later
  /*
  void sync()
  {
  }

  boolean format(int files)
  {
  }

  FileTableEntry open(String filename, String mode)
  {
  }

  boolean close(FileTableEntry entry)
  {
  }
  */

  public static int read(int fd, byte[] buffer)
  {
    return Kernel.OK;
  }

  public static int write(int fd, byte[] buffer)
  {
    return Kernel.OK;
  }

  public static void superblockTest(String cmd,int x,int y,int z)
  {
    superblock.testPrompt(cmd,x,y,z);
  }

  public void InodeTest()
  {
    Inode n0 = new Inode((short)0);
    Inode n1 = new Inode((short)1);
    Inode n2 = new Inode((short)2);

    n0.length = 100;
    n1.count = 6;
    n2.flag = 1;

    n0.toDisk((short)0);
    n1.toDisk((short)1);
    n2.toDisk((short)3);

    SysLib.cout("Inode 0\n" + n0.toString() + "\n");
    SysLib.cout("Inode 1\n" + n1.toString() + "\n");
    SysLib.cout("Inode 2\n" + n2.toString() + "\n");
  }

  /*
   * bytesToInt
   *
   * @param  : location : int : where in the buffer to get the int from
   * @param  : buffer : byte[] : the buffer to get data from
   * @return : int : the integer version of (buffer) at (location)
   *
   * bytesToInt converts 4 bytes from buffer starting at (location)
   * and converts it into an int
   */
  public static int getNextFree()
  {
    int retval = superblock.freeList;
    // get the next free's next (which is the new next free)
    byte[] blockData = new byte[Disk.blockSize];
    SysLib.cread(superblock.freeList,blockData);

    // the next is stored in the first byte
    superblock.freeList = FileSystem.bytesToInt(0,blockData);

    return retval;
  }

  public static void format()
  {
    // TODO: allocate blocks for directory

    superblock.freeList = 6; // guess for now
    byte[] blockData = new byte[Disk.blockSize];
    int currentPrint = 0;

    for(int i=superblock.freeList; i<superblock.totalBlocks; i++)
    {
      SysLib.cread(i,blockData);

      if(i == superblock.totalBlocks - 1)
      {
        // the very last block doesn't link to anywhere, so -1
        intToBytes(0, -1, blockData);
      }
      else
      {
        // otherwise link to the next block
        intToBytes(0, i+1, blockData);
      }

      SysLib.cwrite(i,blockData);

      if((i - superblock.freeList) * 100 / 
         (superblock.totalBlocks - superblock.freeList) != currentPrint)
      {
        SysLib.cout(Integer.toString(currentPrint) + "% ");
        currentPrint = (i - superblock.freeList) * 100 / 
                       (superblock.totalBlocks - superblock.freeList);
      }
    }
  }

  public static int bytesToInt(int location, byte[] buffer)
  {
    int retval = 0;
    for(int i=0; i<4; i++)
    {
      /*
       * let's say the byte we're reading is 0XDEADBEEF
       * at i=0 : retval = (     0 << 8) + DE = 00000000 + DE = DE
       * at i=1 : retval = (    DE << 8) + AD = 0000DE00 + AD = DEAD
       * at i=2 : retval = (  DEAD << 8) + BE = 00DEAD00 + BE = DEADBE
       * at i=3 : retval = (DEADBE << 8) + EF = DEADBE00 + EF = DEADBEEF
       */
      retval = (retval << 8) ^ (buffer[location + i] & 0xFF);
    }
    return retval;
  }

  public static short bytesToShort(int location, byte[] buffer)
  {
    short retval = 0;
    retval = (short) (buffer[location] & 0xFF);
    retval = (short) ((retval << 8) ^ (buffer[location + 1] & 0xFF));
    return retval;
  }

  public static void intToBytes(int location, int num, byte[] buffer)
  {
    buffer[location    ] = ((byte)((num >> 0x18) & 0xFF));
    buffer[location + 1] = ((byte)((num >> 0x10) & 0xFF));
    buffer[location + 2] = ((byte)((num >> 0x08) & 0xFF));
    buffer[location + 3] = ((byte)(num & 0xFF));
  }

  public static void shortToBytes(int location, short num, byte[] buffer)
  {
    buffer[location    ] = ((byte)((num >> 0x8) & 0xFF));
    buffer[location + 1] = ((byte)(num & 0xFF));
  }
}
