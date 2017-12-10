import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;

class FSTest extends Thread
{
  private String target;

  public FSTest()
  {
  }

  public FSTest(String[] args)
  {
  }

  public void run()
  {
    Scanner in = new Scanner(System.in);
    String target;
    int count = 0;
    SysLib.cout(Integer.toString(count++) + " test $ ");
    while(in.hasNext())
    {
      target = in.next();

      if(target.toLowerCase().equals("format"))
      {
        SysLib.cout("max files: ");
        int num = in.nextInt();
        FileSystem.format(num);
      }
      //if(target.toLowerCase().equals("superblock") || 
         //target.toLowerCase().equals("sb"))
      //{
        //FileSystem.superblockTest(cmd,x,y,z);
      //}
      else if(target.toLowerCase().equals("readblock") || 
              target.toLowerCase().equals("rb"))
      {
        SysLib.cout("block number: ");
        int num = in.nextInt();
        printBlock(num);
      }
      else if(target.toLowerCase().equals("inodes"))
      {
        for(int i=0; i<Inode.Inodes.size(); i++)
        {
          SysLib.cout(Integer.toString(i) + ": ");
          if(Inode.getInode(i) != null)
          {
            SysLib.cout(Inode.getInode(i).toString() + "\n");
          }
          else
          {
            SysLib.cout("null\n");
          }
        }
      }
      else if(target.toLowerCase().equals("writeblock") || 
              target.toLowerCase().equals("wb"))
      {
        SysLib.cout("block number: ");
        int b = in.nextInt();
        //Scanner in = new Scanner(System.in);
        int index = 0;
        byte[] buffer = new byte[Disk.blockSize];
        SysLib.cread(b,buffer);

        printBlock(b);
        SysLib.cout("> ");
        while(in.hasNextByte() && index < Disk.blockSize)
        {
          if(index % 4 == 0)
          {
            SysLib.cwrite(b,buffer);
            printBlock(b);
            SysLib.cout("> ");
          }

          buffer[index] = in.nextByte();  
          index++;
        }
      }
      else if(target.toLowerCase().equals("openfile") || 
              target.toLowerCase().equals("of"))
      {
        SysLib.cout("filename: ");
        String filename = in.next();
        SysLib.cout("    mode: ");
        String mode = in.next();

        SysLib.cout("opening file ... ");
        int fd = SysLib.open(filename,mode);
        if(fd != -1)
        {
          SysLib.cout("success (fd = " + Integer.toString(fd) + ")\n");
        }
        else
        {
          SysLib.cout("error: could not open\n");
        }
      }
      else if(target.toLowerCase().equals("readfile") || 
              target.toLowerCase().equals("rf"))
      {
        SysLib.cout("file descriptor: ");
        int fd = in.nextInt();
        SysLib.cout("  bytes to read (-1 for all): ");
        int size = in.nextInt();
        if(fd != -1)
        {

          // fix to-read size
          if(size < 0)
          {
            size = SysLib.fsize(fd);
          }

          //SysLib.cout("file is " + Integer.toString(SysLib.fsize(fd)) + 
          //            " bytes long\n");
          byte[] buffer = new byte[size];
          SysLib.cout("reading from file ...\n");
          SysLib.read(fd,buffer);

          //SysLib.cout("[");
          //for(int i=0; i<buffer.length; i++)
          //{
            //SysLib.cout(Integer.toString(buffer[i]) + " ");
          //}
          //SysLib.cout("]");
          printData(buffer);
        }
        else
        {
          SysLib.cout("cannot open file: DNE\n");
        }
      }
      else if(target.toLowerCase().equals("writefile") || 
              target.toLowerCase().equals("wf"))
      {
        SysLib.cout("file descriptor: ");
        int fd = in.nextInt();
        SysLib.cout("  real filename: ");
        String realFilename = in.next();

        Scanner file;
        if(realFilename.toLowerCase().equals("cin"))
        {
          file = new Scanner(System.in);
        }
        else
        {
          try
          {
            file = new Scanner(new File(realFilename));
          }
          catch(FileNotFoundException e)
          {
            file = new Scanner(System.in);
            SysLib.cout("File \"" + realFilename + 
                        "\" not found; reading from cin\n");
          }
        }

        String input = "";
        while(file.hasNext())
        {
          input += (file.next() + " ");
        }
        byte[] buffer = new byte[input.length()];
        for(int i=0; i<input.length(); i++)
        {
          buffer[i] = (byte)input.charAt(i);
        }

        SysLib.cout("writing to file ...\n");
        SysLib.write(fd,buffer);

        // save changes to disk, just in case
        FileSystem.sync();
      }
      else if(target.toLowerCase().equals("close"))
      {
        SysLib.cout("file descriptor: ");
        int fd = in.nextInt();
        SysLib.close(fd);
      }
      else if(target.toLowerCase().equals("seek"))
      {
        SysLib.cout("file descriptor: ");
        int fd = in.nextInt();
        SysLib.cout("         offset: ");
        int offset = in.nextInt();
        SysLib.cout("         whence: ");
        int whence = in.nextInt();
        SysLib.seek(fd,offset,whence);
      }
      else if(target.toLowerCase().equals("remove") || 
              target.toLowerCase().equals("rm"))
      {
        //Scanner in = new Scanner(System.in);
        SysLib.cout("filename: ");
        String filename = in.next();
        SysLib.delete(filename);
      }
      else if(target.toLowerCase().equals("size"))
      {
        SysLib.cout("file descriptor: ");
        int fd = in.nextInt();
        SysLib.cout("file is " + Integer.toString(SysLib.fsize(fd)) + 
                    " bytes long\n");
      }
      else if(target.toLowerCase().equals("sync"))
      {
        FileSystem.sync();
      }
      else if(target.toLowerCase().equals("exit") || 
              target.toLowerCase().equals("quit") ||
              target.toLowerCase().equals("q"))
      {
        SysLib.cout("bye\n");
        SysLib.exit();
        return;
      }

      SysLib.cout(Integer.toString(count++) + " test $ ");
    }
    SysLib.exit();
  }

  public static void printBlock(int b)
  {
    byte[] buffer = new byte[Disk.blockSize];
    SysLib.cread(b,buffer);
    printData(buffer);
  }

  public static void printData(byte[] buffer)
  {
    String temp = "";

    SysLib.cout(" -    0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F\n");
    for(int i=0; i<buffer.length; i+=16)
    {
      if((i/16) < 16)
      {
        SysLib.cout(" ");
      }
      SysLib.cout(Integer.toHexString(i/16) + " | ");

      for(int j=0; j<16; j++)
      {
        if(i + j >= buffer.length)
        {
          // we ran out of room before the end of the line
          SysLib.cout("   ");
        }
        else
        {
          temp = Integer.toHexString(buffer[i + j] % 0xFF);
          if(temp.length() <= 1)
          {
            SysLib.cout("0" + temp + " ");
          }
          else if(temp.length() == 2)
          {
            SysLib.cout(temp + " ");
          }
          else
          {
            //SysLib.cout(temp + " ");
            SysLib.cout(temp.substring(temp.length()-2,temp.length()) + " ");
          }
        }
      }

      SysLib.cout (" | ");

      for(int j=0; j<16; j++)
      {
        if(i + j >= buffer.length)
        {
          SysLib.cout(" ");
        }
        else
        {
          if(buffer[i + j] >= 0x20 && buffer[i + j] <= 0x7E)
          {
            SysLib.cout(Character.toString((char)(buffer[i + j])));
          }
          else
          {
            SysLib.cout("-");
          }
        }
      }

      SysLib.cout("\n");
    }
  }
}
