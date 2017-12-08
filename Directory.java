public class Directory
{
  // the maximum length of a filename
  private static final int maxNameLength = 30;

  private static final int tableSize = 100;

  private static final int entrySize = 35;  // 35 bytes per entry
  private static final int entriesPerBlock = Disk.blockSize / entrySize;

  private class TableEntry
  {
    public char[] name;
    public byte nameSize;
    public short Inumber;
    public TableEntry next;

    public TableEntry()
    {
      name = new char[maxNameLength];
      nameSize = 0;
      Inumber = 0;
      next = null;
    }
  }

  private Inode inode;        // the inode for this file
  private int maxFileCount;   // maximum number of files we need to store
  private TableEntry[] table; // the hash table itself

  public Directory(int count)
  {
    maxFileCount = count;
    table = new TableEntry[tableSize];
    for(int i=0; i<tableSize; i++)
    {
      table[i] = null;
    }

    //test();
    inode = Inode.getInode(Inode.allocateInode());
  }

  private boolean iexists(String filename)
  { 
    // find the contents of the table at filename's hash
    TableEntry current = table[hash(filename)];
    String s;

    // it's a beginning of a linked list, so while it's not null
    while(current != null)
    {
      s = new String(current.name,0,current.nameSize);
      if(filename.equals(s))
      {
        return true;
      }
      current = current.next;
    }

    return false;
  }

  public short iretrieve(String filename,String mode)
  {
    // find the contents of the table at filename's hash
    TableEntry current = table[hash(filename)];
    String s;

    // it's a beginning of a linked list, so while it's not null
    while(current != null)
    {
      s = new String(current.name,0,current.nameSize);
      if(filename.equals(s))
      {
        return current.Inumber;
      }
      current = current.next;
    }

    if(mode.equals("r"))
    {
      return -1;
    }
    return ialloc(filename);
  }

  public short ialloc(String filename)
  {
    if(iexists(filename))
    {
      return -1;
    }

    short Inum = Inode.allocateInode();
    int hashSlot = hash(filename);

    // make a new entry
    TableEntry t = new TableEntry();
    t.Inumber = Inum;
    t.nameSize = (byte)filename.length();
    for(int i=0; i<filename.length(); i++)
    {
      t.name[i] = filename.charAt(i);
    }

    if(table[hashSlot] == null)
    {
      // the slot is empty, just add it
      table[hashSlot] = t;
    }
    else
    {
      // the slot has something in it, add it to the end
      TableEntry current = table[hashSlot];
      while(current.next != null)
      {
        current = current.next;
      }
      current.next = t;
    }

    return Inum;
  }

  public short ifree(String filename)
  {
    if(!iexists(filename))
    {
      return -1;
    }

    int hashSlot = hash(filename);
    String s;
    TableEntry current = table[hashSlot];
    TableEntry prev = null;
    while(current != null)
    {
      s = new String(current.name,0,current.nameSize); 
      if(filename.equals(s))
      {
        Inode.deleteInode(current.Inumber);
        if(prev == null)
        {
          // this is the first entry in the linked list
          table[hashSlot] = current.next;
        }
        else
        {
          // this is a middle or end link
          prev.next = current.next;
        }
        return current.Inumber;
      }

      prev = current;
      current = current.next;
    }

    return -1;
  }

  private int hash(String s)
  {
    long retval = 0;
    byte temp = 0;

    for(int i=0; i<s.length(); i++)
    {
      // retval = 1234 5678 9ABC DE01 -> temp = 12
      temp = (byte)((retval >> (64 - 8)) & 0xff);
      
      // retval = (3456 789A BCDE 0100 | 12), 3456 789A BCDE 0112
      retval = (retval << 8) | temp;

      // retval = retval XOR the i'th char in s
      retval ^= s.charAt(i);
    }

    return (int)(retval % tableSize);
  }

  public void saveToDisk()
  {
  }

  public void readFromDisk()
  {
  }

  public void test()
  {
    for(int i=0; i<110; i++)
    {
      String s = "number " + Integer.toString(i);
      SysLib.cout("alloc \"" + s + "\" = " + 
                  Integer.toString(ialloc(s)) + "\n");
    }

    SysLib.cout("free \"number 30\" = " + 
                Integer.toString(ifree("number 30")) + "\n");
    SysLib.cout("free \"number 20\" = " + 
                Integer.toString(ifree("number 20")) + "\n");

    SysLib.cout("alloc \"number 110\" = " + 
                Integer.toString(ialloc("number 110")) + "\n");
    SysLib.cout("alloc \"number 111\" = " + 
                Integer.toString(iretrieve("number 111","w+")) + "\n");

    for(int i=111; i>=0; i--)
    {
      String s = "number " + Integer.toString(i);
      SysLib.cout("retr \"" + s + "\" = " + 
                  Integer.toString(iretrieve(s,"r")) + "\n");
    }
  }
}
