/*
 * Directory
 *
 * This is a "folder" in the file system
 *
 * In a normal tree-shaped file system, you can have directories inside of
 * directories, but here it just serves as a way to translate filenames
 * to inode numbers
 *
 * It uses a hash table for retrieval, with chaining for collision resolution
 * (each slot is a linked list)
 */
public class Directory
{
  // the maximum length of a filename
  private static final int maxNameLength = 30;

  // the size of the table (in entries) and the size of an entry (in bytes)
  private static final int tableSize = 100;
  private static final int entrySize = 33;

  /*
   * TableEntry
   *
   * represents a single entry in the table
   */
  private class TableEntry
  {
    public char[] name;     // the name of the file
    public byte nameSize;   // the size of the filename
    public short Inumber;   // the inumber associated with this file
    public TableEntry next; // the next entry (for

    /*
     * constructor
     */
    public TableEntry()
    {
      name = new char[maxNameLength];
      nameSize = 0;
      Inumber = 0;
      next = null;
    }

    /*
     * toBuffer
     *
     * @param  : pos : int : the position to start writing at
     * @param  : buffer : byte[] : the array to write into
     * @return : void
     *
     * writes the entry into the buffer, this is called from toDisk
     * to save the file to the disc
     *
     * next isn't stored because it's re-calculated when it's loaded later
     */
    public void toBuffer(int pos, byte[] buffer)
    {
      FileSystem.shortToBytes(pos,Inumber,buffer);
      pos += 2;
      buffer[pos++] = nameSize;

      for(int i=0; i<maxNameLength; i++)
      {
        if(i < nameSize)
        {
          //SysLib.cout(Integer.toString(pos)+Character.toString(name[i])+" ");
          buffer[pos] = (byte)name[i];
        }
        else
        {
          buffer[pos] = 0;
        }
        pos++;
      }
    }

    /*
     * fromBuffer
     *
     * @param  : pos : int : the position to start reading from
     * @param  : buffer : byte[] : the array to read from
     * @return : void
     *
     * reads and sets the variables from the disk
     *
     * next isn't set because it will be set when it's actually added to its
     * linked list
     */
    public void fromBuffer(int pos, byte[] buffer)
    {
      Inumber = FileSystem.bytesToShort(pos,buffer);
      pos += 2;
      nameSize = buffer[pos++];
      
      for(int i=0; i<maxNameLength; i++)
      {
        if(i < nameSize)
        {
          //SysLib.cout(Character.toString(name[i])+Integer.toString(pos)+" ");
          name[i] = (char)buffer[pos];
        }
        else
        {
          name[i] = 0;
        }
        pos++;
      }
    }

    /*
     * getName
     *
     * @return : String : the name of the file
     *
     * converts the name of the file from a char array to a String 
     * and returns it
     */
    public String getName()
    {
      //SysLib.cout("nameSize = " + Integer.toString(nameSize) + "\n");
      String retval = "";
      for(int i=0; i<nameSize; i++)
      {
        retval += Character.toString(name[i]);
      }
      return retval;
    }

    /*
     * toString
     *
     * @return : String : the file entry as a string
     *
     * converts the file entry into a String and returns it
     */
    public String toString()
    {
      return "| " + Integer.toString(Inumber) + " " + getName() + 
             " (" + Integer.toString(nameSize) + ")";
    }
  }

  private Inode inode;        // the inode for this file
  private int maxFileCount;   // maximum number of files we need to store
  private TableEntry[] table; // the hash table itself

  /*
   * constructor
   *
   * @param : count : int : the maximum number of files the file system can
   *                        support
   *
   * sets up the member variables and loads the data from the disk
   *
   * this is possible because this file is always represented by inode0
   * so we don't need to do any translating to setup the translator
   */
  public Directory(int count)
  {
    maxFileCount = count;
    table = new TableEntry[tableSize];
    for(int i=0; i<tableSize; i++)
    {
      table[i] = null;
    }

    inode = Inode.getInode(0);
    fromDisk();
    //test();
  }

  /*
   * clear
   *
   * @return : void
   *
   * empties the table
   */
  public void clear()
  {
    for(int i=0; i<tableSize; i++)
    {
      table[i] = null;
    }
  }

  /*
   * iexists (inode exists)
   *
   * @param  : filename : String : the filename to search for
   * @return : boolean : whether or not the file exists
   *
   * returns whether or not the file exists in the hash table
   *
   * this is guaranteed not to create a new file
   */
  private boolean iexists(String filename)
  { 
    // find the contents of the table at filename's hash
    TableEntry current = table[hash(filename)];
    String s;

    // it's a beginning of a linked list, so while it's not null ...
    while(current != null)
    {
      // ... get the name of the current entry
      s = current.getName();
      if(filename.equals(s))
      {
        // if it's a match return true
        return true;
      }
      // if not, search the next link (if it's null the while loop will stop)
      current = current.next;
    }

    return false;
  }

  /*
   * iretrieve (inode retrieve)
   *
   * @param  : filename : String : the filename to retrieve
   * @param  : mode : String : the mode we're opening it with
   * @return : short : the inode number associated with the file
   *
   * retrieves the inode number associated with retrieve
   *
   * if the file doesn't exist and the mode is w, w+ or a, it will create a new
   * file and return that, otherwise it returns an error
   */
  public short iretrieve(String filename,String mode)
  {
    // find the contents of the table at filename's hash
    TableEntry current = table[hash(filename)];
    String s;

    // it's a beginning of a linked list, so while it's not null
    while(current != null)
    {
      // get the current name
      s = current.getName();
      if(filename.equals(s))
      {
        // if we find a match returns its inode number
        return current.Inumber;
      }
      // otherwise search the next one
      current = current.next;
    }

    // if we get to here, it's not in the file table
    // So if we're not writing to the file ...
    if(!mode.equals("w") && !mode.equals("w+") && !mode.equals("a"))
    {
      // ... return an error
      return -1;
    }
    // otherwise allocate a new file and return it
    return ialloc(filename);
  }

  /*
   * ialloc (inode allocate)
   *
   * @param  : filename : String : the filename to make
   * @return : short : the inode number of the new file
   *
   * allocates a new file entry (with a new inode) and returns its inode number
   */
  public short ialloc(String filename)
  {
    // don't create a new file if it already exists
    if(iexists(filename))
    {
      //SysLib.cout("inode already exists: -1\n");
      return -1;
    }

    // allocate a new inode, and return failure if it fails
    short Inum = Inode.allocateInode();
    if(Inum == -1)
    {
      //SysLib.cout("ialloc could not allocate node: -1\n");
      return -1;
    }

    // find out where the new file entry goes
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

  /*
   * ifree (inode free)
   *
   * @param  : filename : String : the filename to free
   * @return : short : the inode number that was just deleted
   *
   * removes an entry from the table (and deletes the inode)
   */
  public short ifree(String filename)
  {
    // we can't delete an entry that doesn't exist
    if(!iexists(filename))
    {
      return -1;
    }

    // find out where the entry is
    int hashSlot = hash(filename);
    String s;
    TableEntry current = table[hashSlot];
    TableEntry prev = null;
    while(current != null)
    {
      if(filename.equals(current.getName()))
      {
        //Inode.deleteInode(current.Inumber);
        if(prev == null)
        {
          // this is the first entry in the linked list
          table[hashSlot] = current.next;
        }
        else
        {
          // this is a middle or end link, so have the list skip this entry
          prev.next = current.next;
        }
        return current.Inumber;
      }

      prev = current;
      current = current.next;
    }

    // it wasn't in the list, return -1
    return -1;
  }

  /*
   * hash
   *
   * @param  : s : String : the string to hash
   * @return : int : the hash value of the string
   *
   * a simple hash function
   */
  private int hash(String s)
  {
    // setup variables
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

    // mod the retval so that 0 <= hash < tableSize
    return (int)((retval > 0 ? retval : -retval) % tableSize);
  }

  /*
   * toDisk
   *
   * @return : void
   *
   * writes the contents of the table into the file with inode 0
   */
  public void toDisk()
  {
    // get the inode (don't trust the old version is still valid)
    inode = Inode.getInode(0);

    // convert the data into a byte array
    byte[] buffer = new byte[2 + entrySize * maxFileCount];
    int cursor = 0;
    TableEntry current;
    for(int i=0; i<tableSize; i++)
    {
      current = table[i]; 
      while(current != null)
      {
        // write each entry to the file
        current.toBuffer(2 + cursor * entrySize, buffer);
        current = current.next;
        cursor++;
      }
    }

    // store the total count at the very beginning
    FileSystem.shortToBytes(0,(short)cursor,buffer);

    //FSTest.printData(buffer);

    // write it to inode 0
    FileTableEntry fte = new FileTableEntry(inode,(short)0,"w");
    FileSystem.write(fte,buffer);
  }

  /*
   * fromDisk
   *
   * @return : void
   *
   * reads and initializes the table from the disk, specifically the file
   * described by inode 0
   */
  public void fromDisk()
  {
    // get the inode (don't trust the old version is still valid)
    inode = Inode.getInode(0);
    if(inode == null || inode.length <= 0)
    {
      // fail if the inode doesn't exist or it's invalid
      return;
    }

    // read it from inode 0
    byte[] buffer = new byte[2 + entrySize * maxFileCount];
    FileTableEntry fte = new FileTableEntry(inode,(short)0,"r");
    FileSystem.read(fte,buffer);

    //FSTest.printData(buffer);

    // convert the data into a byte array
    short count = FileSystem.bytesToShort(0,buffer);
    int cursor = 0;
    int index;
    TableEntry current;
    for(int i=0; i<count; i++)
    {
      current = new TableEntry();
      current.fromBuffer(2 + cursor * entrySize, buffer);
      
      // find out where the entry is
      index = hash(current.getName());
      if(table[index] == null)
      {
        // the slot is empty, just add it
        table[index] = current;
      }
      else
      {
        // there's something here, find the tail of the linked list and
        // add it after it
        TableEntry tail = table[index];
        while(tail.next != null)
        {
          tail = tail.next;
        }
        tail.next = current;
      }
      cursor++;
    }
  }

  /*
   * print
   *
   * @return : void
   *
   * prints the table to the screen
   *
   * this is a hash table, so they're sorted by hash value, not by name or
   * inode number.  Sorting by name would require adding them all to a list,
   * sort it, and print from that.
   */
  public void print()
  {
    SysLib.cout("+---------------\n");
    for(int i=0; i<tableSize; i++)
    {
      TableEntry current = table[i];
      while(current != null)
      {
        SysLib.cout(current.toString() + "\n");
        current = current.next;
      }
    }
    SysLib.cout("+---------------\n");
  }

  /*
   * test
   *
   * @return : void
   *
   * tests if the hash table is adding and removing things properly
   */
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
