import java.util.Vector;

public class Inode
{
  // for synchronization purposes, as inodes will be accessed by multiple
  // concurrent threads

  // Vector is synchronized while ArrayList is not, so this is safe for
  // multi-threadded programs
  public static Vector<Inode> Inodes = new Vector<Inode>();

  // design decisions
  public static final int inodeSize  = 32;  // an inode is 32 bytes
  public static final int directSize = 11;  // number or direct pointers to
                                            // disk blocks
  private static int maxCount = -1;         // maximum inodes the file
                                            // system can support

  // for the flag
  public static final short FLAG_UNUSED  = 0;
  public static final short FLAG_USED    = 1;
  public static final short FLAG_DELETED = 2;
  
  // member variables
  public int length;     // length of the file in bytes
  public short count;    // number of file table entries pointing to this inode
  public short flag;     // state of the inode
  public short direct[] = new short[directSize]; // direct pointers to data
  public short indirect; // indirect pointer

  public static boolean setMaxCount(int i)
  {
    // it starts at -1, so if it's already set don't set it again
    if(maxCount == -1)
    {
      maxCount = i;
      return true;
    }
    return false;
  }

  /*
   * default constructor
   */
  public Inode()
  {
    length = 0;
    count = 0;
    flag = FLAG_USED;
    for(int i=0; i<directSize; i++)
    {
      direct[i] = -1;
    }
    indirect = -1;
  }
  
  /*
   * constructor that retrieves data from disc
   * (assuming this is the Inumber-th Inode)
   *
   */
  public Inode(short Inumber)
  {
    byte[] buffer = new byte[Disk.blockSize];
    short blockNumber = 
          (short)(((Inumber * inodeSize ) / Disk.blockSize) + 1);
    short position    = 
          (short) ((Inumber * inodeSize ) % Disk.blockSize);

    SysLib.cread(blockNumber,buffer);

    length = FileSystem.bytesToInt(position,buffer);  // get the data
    position += 4;  // ints are 4 bytes, so read from the next bit next time

    count = FileSystem.bytesToShort(position,buffer);  // get the data
    position += 2;

    flag = FileSystem.bytesToShort(position,buffer);
    position += 2;

    for(int i=0; i<directSize; i++)
    {
      direct[i] = FileSystem.bytesToShort(position,buffer);
      position += 2;
    }

    indirect = FileSystem.bytesToShort(position,buffer);
  }

  public int toDisk(short Inumber)
  {
    byte[] buffer = new byte[Disk.blockSize];
    short blockNumber = 
          (short)(((Inumber * inodeSize ) / Disk.blockSize) + 1);
    short position    = 
          (short) ((Inumber * inodeSize ) % Disk.blockSize);

    SysLib.cread(blockNumber,buffer);

    FileSystem.intToBytes(position,length,buffer);  // get the data
    position += 4;  // ints are 4 bytes, so read from the next bit next time

    FileSystem.shortToBytes(position,count,buffer);  // get the data
    position += 2;

    FileSystem.shortToBytes(position,flag,buffer);
    position += 2;

    for(int i=0; i<directSize; i++)
    {
      FileSystem.shortToBytes(position,direct[i],buffer);
      position += 2;
    }

    FileSystem.shortToBytes(position,indirect,buffer);

    SysLib.cwrite(blockNumber,buffer);

    // add it to the vector
    //while(Inodes.size() < Inumber)
    //{
      //Inodes.add(null);
    //}
    //Inodes.add(this);

    return 0;
  }

  public static void allToDisk()
  {
    // update the inode count (in superblock)
    byte[] buffer = new byte[Disk.blockSize];
    SysLib.cread(0,buffer);
    FileSystem.intToBytes(12,Inodes.size(),buffer);
    SysLib.cwrite(0,buffer);

    // if an entry in the vector is null, we write a "inode" with length = -1
    // so we know to leave a gap in when we're booting up later
    Inode dummy = new Inode();
    dummy.length = -1;

    for(short i=0; i<Inodes.size(); i++)
    {
      if(Inodes.get(i) != null)
      {
        Inodes.get(i).toDisk(i);
      }
      else
      {
        dummy.toDisk(i);
      }
    }
  }

  public static void readAllFromDisk()
  {
    // update the inode count (in superblock)
    byte[] buffer = new byte[Disk.blockSize];
    SysLib.cread(0,buffer);
    int count = FileSystem.bytesToInt(12,buffer);
    Inode temp;

    for(short i=0; i<count; i++)
    {
      temp = new Inode(i);
      if(temp.length != -1)
      {
        Inodes.add(temp);  
      }
      else
      {
        Inodes.add(null);
      }
    }
  }

  public String toString()
  {
    String retval = "";
    retval += ("l=" + Integer.toString(length) + " ");
    retval += ("c=" + Short.toString(count)    + " ");
    retval += ("f=" + Short.toString(flag)     + " ");
    retval += "d=[";
    for(int i=0; i<directSize; i++)
    {
      retval += Short.toString(direct[i]);
      if(i < directSize-1)
      {
        retval += ",";
      }
      else
      {
        retval += "] ";
      }
    }
    retval += ("i=" + Short.toString(indirect));
    return retval;
  }

  public static Inode getInode(int i)
  {
    if(i >= 0 & i < Inodes.size())
    {
      return Inodes.get(i);
    }
    return null;
  }

  public static short allocateInode()
  {
    // add an item to the vector (replacing a null if there is one)
    for(short i=0; i<Inodes.size(); i++)
    {
      if(Inodes.get(i) == null)
      {
        // this is an empty slot, so use it
        Inodes.set(i,new Inode());
        return i;
      }
    }

    // we didn't find a gap to fill, so don't add one if we're at full capacity
    if(Inodes.size() == maxCount)
    {
      return (short)(-1);
    }

    Inodes.add(new Inode());
    return (short)(Inodes.size() - 1);
  }

  public static short seekPointerToBlock(int seek, int Inumber)
  {
    Inode i = Inodes.get(Inumber);
    if(i == null)
    {
      return -1;
    }

    // check if it's in a direct block (seek is below directSize * blockSize)
    if(seek < directSize * Disk.blockSize)
    {
      int index = seek / Disk.blockSize;
      return i.direct[index];
    }

    // it's in the indirect block somewhere
    int index = (seek / Disk.blockSize) - directSize;
    byte[] indirectBlock = new byte[Disk.blockSize];
    SysLib.cread(i.indirect,indirectBlock);
    return FileSystem.bytesToShort(index * 2,indirectBlock);
  }

  public static boolean deleteInode(int Inumber)
  {
    if(Inumber >= 0 && Inumber < Inodes.size() && Inodes.get(Inumber) != null)
    {
      FileSystem.freeInode(Inodes.get(Inumber));
      Inodes.set(Inumber,null);
      return true;
    }
    return false;
  }
}
