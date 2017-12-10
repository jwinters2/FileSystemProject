import java.util.Scanner;

public class FileSystem
{
  private static final boolean SKIP_FORMAT_FREE_LINKING = false;

  public static final int SEEK_SET = 0;
  public static final int SEEK_CUR = 1;
  public static final int SEEK_END = 2;

  private static Superblock superblock;
  private static Directory directory;
  private static FileTable filetable;
  
  public FileSystem(int diskBlocks)
  {
    superblock = new Superblock(diskBlocks);
    Inode.setMaxCount(superblock.totalInodes);
    Inode.readAllFromDisk();

    directory = new Directory(superblock.totalInodes);
    filetable = new FileTable(directory);

    //InodeTest();
  }

  public static boolean format(int files)
  {
    // set superblock variables
    superblock.totalInodes = files;

    superblock.freeList = 2 + (files-1)/16;
    if(files == 0)
    {
      superblock.freeList = 2;
    }

    // reset inode list (except for one, there's always one for / )
    Inode.Inodes.clear();
    Inode.allocateInode();

    // reset directory and fileTable
    directory.clear();
    filetable.clear();

    // clear the superblock and inode-reserved blocks
    byte[] blockData = new byte[Disk.blockSize];
    for(int i=0; i<Disk.blockSize; i++)
    {
      blockData[i] = 0;
    }
    for(int i=0; i<superblock.freeList; i++)
    {
      SysLib.cwrite(i,blockData);
    }

    // write superblock data to block 0 and inode 0 date to block 1
    superblock.toDisk();
    Inode.allToDisk();

    if(!SKIP_FORMAT_FREE_LINKING)
    {
      //SysLib.cout("\n|-----20%-|-----40%-|-----60%-|-----80%-|----100%-|\n");
      int currentPrint = 0;

      // every other block is a free block, which needs the index of the next
      // free block to make a chain
      for(int i=superblock.freeList; i<superblock.totalBlocks; i++)
      {
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
      
        if((i - superblock.freeList) * 51 / 
           (superblock.totalBlocks - superblock.freeList) != currentPrint)
        {
          currentPrint = (i - superblock.freeList) * 51 / 
                         (superblock.totalBlocks - superblock.freeList);
          //SysLib.cout("#");
        }
      }
      //SysLib.cout("#\n");
    }

    return true;
  }

  public static void sync()
  {
    superblock.toDisk();
    Inode.allToDisk();
    directory.toDisk();
  }

  public static int read(FileTableEntry fte, byte[] output)
  {
    if(fte == null || fte.inode == null || 
       fte.Mode.equals("w") || fte.Mode.equals("a"))
    {
      //SysLib.cout("error: mode is " + fte.Mode + "\n");
      return Kernel.ERROR;
    }

    Inode inode = fte.inode;
    //SysLib.cout("READ inode: " + inode.toString() + "\n");
    //SysLib.cout("      inum:" + Integer.toString(fte.iNumber) + "\n");
    //SysLib.cout("   seekptr:" + Integer.toString(fte.seekPtr) + "\n");
    byte[] buffer = new byte[Disk.blockSize];

    int bytesRead = 0;
    int block = 0;
    int blockLength = 0;
    int address = 0;
    while(bytesRead < output.length && fte.seekPtr < inode.length)
    {
      // calculate the block and actual address from the seekPtr
      block = Inode.seekPointerToBlock(fte.seekPtr,fte.iNumber);
      address = block * Disk.blockSize + fte.seekPtr % Disk.blockSize;
      //SysLib.cout("block: " + Integer.toString(block) + "\n");
      //SysLib.cout("addr : " + Integer.toString(address) + "\n");
      SysLib.cread(block,buffer);
      
      // start by assuming we're reading the entire block from memory
      blockLength = Disk.blockSize;

      if(block * Disk.blockSize < address)
      {
        // we're starting somewhere in the middle of the block
        blockLength -= address - (block * Disk.blockSize);
      }
      if( (block+1) * Disk.blockSize - address > output.length - bytesRead)
      {
        // we're ending before the end of the block
        blockLength -= ((block+1) * Disk.blockSize)
                     - (address + output.length - bytesRead);
      }

      System.arraycopy(buffer,fte.seekPtr % Disk.blockSize,
                       output,bytesRead,blockLength);

      bytesRead += blockLength;
      fte.seekPtr += blockLength;

      //SysLib.cout("bytes read: " + Integer.toString(bytesRead) + "\n");
      //SysLib.cout("seekptr   : " + Integer.toString(fte.seekPtr) + "\n");
    }
    
    //FSTest.printData(output);

    return bytesRead;
  }

  public static int write(FileTableEntry fte, byte[] output)
  {
    if(fte == null || fte.inode == null || fte.Mode.equals("r"))
    {
      return Kernel.ERROR;
    }

    Inode inode = fte.inode;
    byte[] buffer = new byte[Disk.blockSize];

    int bytesWritten = 0;
    int block = 0;
    int blockLength = 0;
    int address = 0;
    while(bytesWritten < output.length)
    {
      // if we hit the end of the file, allocate a new block for it
      if(fte.seekPtr >= inode.length)
      {
        //SysLib.cout("allocating new block for inode\n");
        allocateBlock(inode);
      }

      // calculate the block and actual address from the seekPtr
      block = Inode.seekPointerToBlock(fte.seekPtr,fte.iNumber);
      address = block * Disk.blockSize + fte.seekPtr % Disk.blockSize;
      //SysLib.cout("inode = " + inode.toString() + "\n");
      //SysLib.cout("addr  = " + Integer.toString(address) + "\n");
      //SysLib.cout("block = " + Integer.toString(block)   + "\n");
      SysLib.cread(block,buffer);
      
      // start by assuming we're writing the entire block into memory
      blockLength = Disk.blockSize;

      if(block * Disk.blockSize < address)
      {
        // we're starting somewhere in the middle of the block
        blockLength -= address - (block * Disk.blockSize);
      }
      if( (block+1) * Disk.blockSize > address + output.length - bytesWritten)
      {
        // we're ending before the end of the block
        blockLength -= ((block+1) * Disk.blockSize)
                     - (address + output.length - bytesWritten);
      }

      System.arraycopy(output,bytesWritten,
                       buffer,fte.seekPtr % Disk.blockSize,blockLength);
      SysLib.cwrite(block,buffer);

      bytesWritten += blockLength;
      fte.seekPtr += blockLength;

      if(fte.seekPtr > inode.length)
      {
        inode.length = fte.seekPtr;
      }
    }

    return bytesWritten;
  }

  public static FileTableEntry open(String filename, String mode)
  {
    FileTableEntry retval = filetable.fretrieve(filename,mode);
    sync();
    return retval;
  }

  public static boolean close(FileTableEntry fte)
  {
    return filetable.ffree(fte); 
  }

  public static int seek(FileTableEntry fte,int offset,int whence)
  {
    switch(whence)
    {
      case SEEK_SET:
          fte.seekPtr = offset;
          break;

      case SEEK_CUR:
          fte.seekPtr += offset;
          break;

      case SEEK_END:
          fte.seekPtr = fte.inode.length + offset;
          break;

      default:
        return Kernel.ERROR;
    }

    if(fte.seekPtr < 0)
    {
      fte.seekPtr = 0;
    }
    if(fte.seekPtr > fte.inode.length)
    {
      fte.seekPtr = fte.inode.length;
    }
    return fte.seekPtr;
  }

  public static int delete(String filename)
  {
    short inum = directory.ifree(filename);
    if(inum == -1)
    {
      return Kernel.ERROR;
    }

    FileTableEntry fte = filetable.getByInum(inum);

    if(fte == null)
    {
      // if fte is null then it wasn't in the filetable, therefore it's not open
      // just delete it
      Inode.deleteInode(inum);
      return Kernel.OK;
    }
    else
    {
      // it's open somewhere, just set it to deleted
      fte.inode.flag = Inode.FLAG_DELETED;
      //return Kernel.ERROR;
    }
    return Kernel.OK;
  }

  public static short deleteFromDirectory(String filename)
  {
    return directory.ifree(filename);
  }

  public static void superblockTest(String cmd,int x,int y,int z)
  {
    superblock.testPrompt(cmd,x,y,z);
  }

  public void InodeTest()
  {
    Inode n0 = new Inode((short)0);

    n0.length = 21;
    n0.direct = new short[] {20,22,24,26,28, 30,31,32,33,34,35};
    n0.indirect = 100;

    byte[] buffer = new byte[Disk.blockSize];
    for(int i=0; i<Disk.blockSize; i+=2)
    {
      shortToBytes(i,(short)((i/2)+150),buffer);
    }
    SysLib.cwrite(n0.indirect,buffer);

    n0.toDisk((short)0);

    SysLib.cout("Inode 0\n" + n0.toString() + "\n");

    Scanner in = new Scanner(System.in);
    int s;
    while(in.hasNextInt())
    {
      s = in.nextInt();
      SysLib.cout(" -> " + Integer.toString(Inode.seekPointerToBlock(s,0)) 
                  + "\n");
    }
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
  public static short getNextFree()
  {
    int retval = superblock.freeList;
    // get the next free's next (which is the new next free)
    //SysLib.cout("free block = "+Integer.toString(superblock.freeList) + "\n");
    byte[] blockData = new byte[Disk.blockSize];
    SysLib.cread(superblock.freeList,blockData);

    // the next is stored in the first byte
    superblock.freeList = FileSystem.bytesToInt(0,blockData);

    return (short)retval;
  }

  private static boolean allocateBlock(Inode inode)
  {
    byte[] blockData = new byte[Disk.blockSize];
    short block = getNextFree();

    // add it to the first empty direct slot
    for(int i=0; i<Inode.directSize; i++)
    {
      if(inode.direct[i] == -1)
      {
        inode.direct[i] = block;
        return true;
      }
    }

    // that didn't work; add it to an indirect one

    // first, give it an indirect block if it doesn't already have one
    if(inode.indirect == -1)
    {
      // give it a free block
      inode.indirect = getNextFree();

      // -1 it out (except for the first real block
      SysLib.cread(inode.indirect, blockData);
      shortToBytes(0,block,blockData);
      for(int i=2; i<Disk.blockSize; i+=2)
      {
        shortToBytes(i,(short)(-1),blockData);
      }
      SysLib.cwrite(inode.indirect, blockData);
      return true;
    }
    else
    {
      // the block already exists, find the first -1 and write the block to it
      SysLib.cread(inode.indirect, blockData);
      for(int i=0; i<Disk.blockSize; i+=2)
      {
        if(bytesToShort(i,blockData) == -1)
        {
          shortToBytes(i,block,blockData);
          SysLib.cwrite(inode.indirect, blockData);
          return true;
        }
      }
    }
    return false;
  }

  public static void freeBlock(short block)
  {
    byte[] buffer = new byte[Disk.blockSize];
    intToBytes(0,superblock.freeList,buffer);
    SysLib.cwrite(block,buffer);
    superblock.freeList = block;
  }

  public static void freeInode(Inode inode)
  {
    for(int i=0; i<Inode.directSize; i++) 
    {
      if(inode.direct[i] != -1)
      {
        freeBlock(inode.direct[i]);
      }
    }

    if(inode.indirect != -1)
    {
      byte[] blockData = new byte[Disk.blockSize];
      SysLib.cread(inode.indirect,blockData);

      for(int i=0; i<Disk.blockSize; i+=2)
      {
        short block = bytesToShort(i,blockData);
        if(block != -1)
        {
          freeBlock(block);
        }
      }
      freeBlock(inode.indirect);
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
