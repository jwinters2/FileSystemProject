import java.util.Scanner; // only used to testing

/*
 * FileSystem
 *
 * this is the main interface for the file system
 *
 * all the SysLib calls go to the kernel than straight to here, where
 * it goes to the proper object, and eventually to the spinny memory thing
 */
public class FileSystem
{
  // constants for seek
  public static final int SEEK_SET = 0;
  public static final int SEEK_CUR = 1;
  public static final int SEEK_END = 2;

  // superblock, directory and filetable
  private static Superblock superblock;
  private static Directory directory;
  private static FileTable filetable;
  
  /*
   * constructor
   *
   * @param : diskBlocks : int : the size of the disk in blocks
   *
   * sets up the variables, initalized the superblock, directory and filetable
   */
  public FileSystem(int diskBlocks)
  {
    // initialize the superblock (with the number of blocks)
    superblock = new Superblock(diskBlocks);

    // sets up the inode and loads them from the disk
    Inode.setMaxCount(superblock.totalInodes);
    Inode.readAllFromDisk();

    // setup the directory with the max file count, and the filetable with the
    // directory
    directory = new Directory(superblock.totalInodes);
    filetable = new FileTable(directory);

    //InodeTest();
  }

  /*
   * format
   *
   * @param  : files : int : the maximum number of files we'll need to support
   * @return : boolean : whether or not it worked
   *
   * formats the disk, setting it up with a valid, empty filesystem
   *
   * like actual formatting, it takes a while
   */
  public static boolean format(int files)
  {
    // set superblock variables
    superblock.totalInodes = files;

    superblock.freeList = 2 + (files-1)/16;
    if(files == 0)
    {
      superblock.freeList = 2;
    }

    // set Inode max count to the new value
    Inode.setMaxCount(superblock.totalInodes);

    // reset inode list (except for one, there's always one for / )
    Inode.Inodes.clear();
    Inode.allocateInode();

    // reset directory and fileTable
    //directory.clear();
    //filetable.clear();

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

    // re-initialize directory and filetable to use the new values
    directory = new Directory(superblock.totalInodes);
    filetable = new FileTable(directory);

    //SysLib.cout("totalBlocks = " + 
    //            Integer.toString(superblock.totalBlocks) + "\n");
    //SysLib.cout("totalInodes = " + 
    //            Integer.toString(superblock.totalInodes) + "\n");
    //SysLib.cout("freeList    = " + 
    //            Integer.toString(superblock.freeList) + "\n");

    return true;
  }

  /*
   * sync
   *
   * @param : void
   *
   * saves all the data to the disc
   */
  public static void sync()
  {
    superblock.toDisk();
    Inode.allToDisk();
    directory.toDisk();
  }

  /*
   * read
   *
   * @param  : fte : FileTableEntry : the file table entry to read from
   * @param  : output : byte[] : the byte array to put the data into
   *
   * reads from a file
   *
   * the FileTableEntry is extracted from this thread's TCB by the kernel
   */
  public static int read(FileTableEntry fte, byte[] output)
  {
    // reading should fail if
    // - the FileTableEntry is null
    // - the FileTableEntry's inode is null
    // - the file is open as w or a
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

    int bytesRead = 0;    // the running total number of bytes read
    int block = 0;        // the current block we're reading from
    int blockLength = 0;  // the length of the segment we're reading
    int address = 0;      // the real address in the disk
    while(bytesRead < output.length && fte.seekPtr < inode.length)
    {
      // calculate the block and actual address from the seekPtr
      block = Inode.seekPointerToBlock(fte.seekPtr,fte.iNumber);
      address = block * Disk.blockSize + fte.seekPtr % Disk.blockSize;
      //SysLib.cout("block: " + Integer.toString(block) + "\n");
      //SysLib.cout("addr : " + Integer.toString(address) + "\n");

      // read the block from the disc
      SysLib.cread(block,buffer);
      
      // start by assuming we're reading the entire block from memory
      blockLength = Disk.blockSize;

      if(block * Disk.blockSize < address)
      {
        // we're starting somewhere in the middle of the block
        // subtract the distance from the beginning of the block to the address
        blockLength -= address - (block * Disk.blockSize);
      }
      if( (block+1) * Disk.blockSize - address > output.length - bytesRead)
      {
        // we're ending before the end of the block
        // subtract the distance from where we'll run out of space
        // to the end of the block
        blockLength -= ((block+1) * Disk.blockSize)
                     - (address + output.length - bytesRead);
      }

      // copy the data from the buffer
      System.arraycopy(buffer,fte.seekPtr % Disk.blockSize,
                       output,bytesRead,blockLength);

      // update the total bytes read and the seek pointer
      bytesRead += blockLength;
      fte.seekPtr += blockLength;

      //SysLib.cout("bytes read: " + Integer.toString(bytesRead) + "\n");
      //SysLib.cout("seekptr   : " + Integer.toString(fte.seekPtr) + "\n");
    }
    
    //FSTest.printData(output);

    return bytesRead;
  }

  /*
   * write
   *
   * @param  : fte : FileTableEntry : the file table entry to write into
   * @param  : output : byte[] : the byte array to get the data from
   *
   * writes to a file
   *
   * the FileTableEntry is extracted from this thread's TCB by the kernel
   */
  public static int write(FileTableEntry fte, byte[] output)
  {
    // writing should fail if
    // - the FileTableEntry is null
    // - the FileTableEntry's inode is null
    // - the file is open as r
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
        // subtract the distance from the beginning of the block to the address
        blockLength -= address - (block * Disk.blockSize);
      }
      if( (block+1) * Disk.blockSize > address + output.length - bytesWritten)
      {
        // we're ending before the end of the block
        // subtract the distance from where we'll run out of space
        // to the end of the block
        blockLength -= ((block+1) * Disk.blockSize)
                     - (address + output.length - bytesWritten);
      }

      // copy from the output to the buffer
      System.arraycopy(output,bytesWritten,
                       buffer,fte.seekPtr % Disk.blockSize,blockLength);
      // write to the disc
      SysLib.cwrite(block,buffer);

      // update the total bytes read and the seek pointer
      bytesWritten += blockLength;
      fte.seekPtr += blockLength;

      // update the length of the inode
      if(fte.seekPtr > inode.length)
      {
        inode.length = fte.seekPtr;
      }
    }

    return bytesWritten;
  }

  /*
   * open
   *
   * @param  : filename : String : name of the file to open
   * @param  : mode : String : the mode to open it with
   * @return : FileTableEntry : the file table entry associated with the file
   *
   * opens the file (retrieves it from the directory, which will allocate a new
   * one if it doesn't exist and the mode isn't r, and add it to the file table
   */
  public static FileTableEntry open(String filename, String mode)
  {
    FileTableEntry retval = filetable.fretrieve(filename,mode);
    // sync to save the changes to the directory and filetable
    // in case a later operation crashes without syncing
    sync();
    return retval;
  }

  /*
   * close
   *
   * @param  : fte : FileTableEntry : the file table entry to close
   * @return : boolean : whether or not it works
   *
   * closes the file
   */
  public static boolean close(FileTableEntry fte)
  {
    return filetable.ffree(fte); 
  }

  /*
   * seek
   *
   * @param  : fte : FileTableEntry : the file table entry associated
   *                                  with the file
   * @param  : offset : int : the offset to change the seek pointer by
   * @param  : whence : int : where to set the pointer from
   *                          SEEK_SET : from the beginning of the file
   *                          SEEK_CUR : from the current seek pointer
   *                          SEEK_END : from the end of the file
   * @return : int : the new value of the seek pointer
   *
   * changes the seek pointer of the file
   * it must be 0 <= seekPtr <= length
   */
  public static int seek(FileTableEntry fte,int offset,int whence)
  {
    // fail if the FileTableEntry or its inode is null
    if(fte == null || fte.inode == null)
    {
      return -1;
    }

    switch(whence)
    {
      case SEEK_SET:
        // set it from the beginning
        fte.seekPtr = offset;
        break;

      case SEEK_CUR:
        // set it from the current offset
        fte.seekPtr += offset;
        break;

      case SEEK_END:
        // set it from the end
        fte.seekPtr = fte.inode.length + offset;
        break;

      default:
        return Kernel.ERROR;
    }

    // if it's before the beginning, set it to the beginning
    if(fte.seekPtr < 0)
    {
      fte.seekPtr = 0;
    }

    // if it's after the end, set it to the end
    if(fte.seekPtr > fte.inode.length)
    {
      fte.seekPtr = fte.inode.length;
    }

    // return the new seek pointer
    return fte.seekPtr;
  }

  /*
   * delete
   *
   * @param  : filename : String : the filename to delete
   * @return : int : the success value of the operation
   *
   * deletes the file
   *
   * it takes the filename and not a FileTableEntry because it needs to delete
   * it from the directory, and the directory hashes by filename, therefore
   * we need to know the filename
   *
   * and it's easier to get the inumber from the filename than the other
   * way around
   */
  public static int delete(String filename)
  {
    // if ifree fails, it failed
    short inum = directory.ifree(filename);
    if(inum == -1)
    {
      return Kernel.ERROR;
    }

    // we need the entire FileTableEntry
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

  /*
   * listDirectory
   *
   * @return : void
   *
   * this is just a wrapper for Directory::print()
   */
  public static void listDirectory()
  {
    directory.print();
  }

  /*
   * superblockTest
   *
   * @param  : cmd : String : the command to pass to the superblock
   * @param  : x : int : some parameters to pass along
   * @param  : y : int : some parameters to pass along
   * @param  : z : int : some parameters to pass along
   *
   * just passes a test along to the superblock
   */
  public static void superblockTest(String cmd,int x,int y,int z)
  {
    superblock.testPrompt(cmd,x,y,z);
  }

  /*
   * InodeTest
   *
   * @return : void
   *
   * tests the inodes to see if seekPointerToBlock works
   */
  public void InodeTest()
  {
    // setup an inode
    Inode n0 = new Inode((short)0);

    n0.length = 21;
    n0.direct = new short[] {20,22,24,26,28, 30,31,32,33,34,35};
    n0.indirect = 100;

    // write an indirect block
    byte[] buffer = new byte[Disk.blockSize];
    for(int i=0; i<Disk.blockSize; i+=2)
    {
      shortToBytes(i,(short)((i/2)+150),buffer);
    }
    SysLib.cwrite(n0.indirect,buffer);

    // save the inode to disc
    n0.toDisk((short)0);

    SysLib.cout("Inode 0\n" + n0.toString() + "\n");

    // take a file address from the user and translate it to a block number
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
   * getNextFree
   *
   * @return : short : the blockId for the next fresh free block
   *
   * pops the next free block from the list and returns it
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

  /*
   * allocateBlock
   *
   * @param  : inode : Inode : the inode to allocate a new block to
   * @return : boolean : whether or not it worked
   *
   * gets the next free block from the free list and gives it to the inode
   */
  private static boolean allocateBlock(Inode inode)
  {
    // it failed if inode is null
    if(inode == null)
    {
      return false;
    }

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

      SysLib.cread(inode.indirect, blockData);
      // the first short is the block number
      shortToBytes(0,block,blockData);
      for(int i=2; i<Disk.blockSize; i+=2)
      {
        // all other blocks are -1
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

    // if it gets to here the inode ran out of room
    return false;
  }

  /*
   * freeBlock
   *
   * @param  : block : short : the block number to add to the free list
   * @return : void
   *
   * adds a block to the free list
   */
  public static void freeBlock(short block)
  {
    byte[] buffer = new byte[Disk.blockSize];
    intToBytes(0,superblock.freeList,buffer);
    SysLib.cwrite(block,buffer);
    superblock.freeList = block;
  }

  /*
   * freeInode
   *
   * @param  : inode : Inode : the inode to allocate a new block to
   * @return : void
   * 
   * frees all the blocks associated with this inode
   */
  public static void freeInode(Inode inode)
  {
    // free the direct blocks
    for(int i=0; i<Inode.directSize; i++) 
    {
      if(inode.direct[i] != -1)
      {
        freeBlock(inode.direct[i]);
      }
    }

    // if there's an indirect block
    if(inode.indirect != -1)
    {
      // read the indirect block
      byte[] blockData = new byte[Disk.blockSize];
      SysLib.cread(inode.indirect,blockData);

      // free all the block numbers in the indirect block
      for(int i=0; i<Disk.blockSize; i+=2)
      {
        short block = bytesToShort(i,blockData);
        if(block != -1)
        {
          freeBlock(block);
        }
      }

      // free the indirect block itself
      freeBlock(inode.indirect);
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

  /*
   * bytesToShort
   *
   * @param  : location : int : where in the buffer to get the short from
   * @param  : buffer : byte[] : the buffer to get data from
   * @return : short : the short version of (buffer) at (location)
   *
   * bytesToShort converts 2 bytes from buffer starting at (location)
   * and converts it into a short
   */
  public static short bytesToShort(int location, byte[] buffer)
  {
    short retval = 0;
    retval = (short) (buffer[location] & 0xFF);
    retval = (short) ((retval << 8) ^ (buffer[location + 1] & 0xFF));
    return retval;
  }

  /*
   * intToBytes
   *
   * @param  : location : int : where in the buffer to put the int
   * @param  : buffer : byte[] : the buffer to write to
   * @param  : int : the integer to write into (buffer) at (location)
   * @return : void
   *
   * intToBytes writes an int into the buffer, at the 4 bytes 
   * starting at location
   */
  public static void intToBytes(int location, int num, byte[] buffer)
  {
    buffer[location    ] = ((byte)((num >> 0x18) & 0xFF));
    buffer[location + 1] = ((byte)((num >> 0x10) & 0xFF));
    buffer[location + 2] = ((byte)((num >> 0x08) & 0xFF));
    buffer[location + 3] = ((byte)(num & 0xFF));
  }

  /*
   * shortToBytes
   *
   * @param  : location : int : where in the buffer to put the short
   * @param  : buffer : byte[] : the buffer to write to
   * @param  : short : the short to write into (buffer) at (location)
   * @return : void
   *
   * shortToBytes writes an int into the buffer, at the 2 bytes 
   * starting at location
   */
  public static void shortToBytes(int location, short num, byte[] buffer)
  {
    buffer[location    ] = ((byte)((num >> 0x8) & 0xFF));
    buffer[location + 1] = ((byte)(num & 0xFF));
  }
}
