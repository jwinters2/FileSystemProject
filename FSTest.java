class FSTest extends Thread
{
  private String target;
  private String cmd;
  private int x;
  private int y;
  private int z;

  public FSTest()
  {
    target = "";
    cmd = "";
    x = 0;
    y = 0;
    z = 0;
  }

  public FSTest(String[] args)
  {
    target = args[0];
    cmd = args[1];

    if(args.length > 4)
    {
      x = Integer.parseInt(args[2]);
      y = Integer.parseInt(args[3]);
      z = Integer.parseInt(args[4]);
    }
    else
    {
      x = 0;
      y = 0;
      z = 0;
    }
  }

  public void run()
  {
    if(target.toLowerCase().equals("superblock") || 
       target.toLowerCase().equals("sb"))
    {
      FileSystem.superblockTest(cmd,x,y,z);
    }
    else if(target.toLowerCase().equals("readblock") || 
            target.toLowerCase().equals("rb"))
    {
      String temp = "";
      byte[] buffer = new byte[Disk.blockSize];
      SysLib.cread(Integer.parseInt(cmd),buffer);

      SysLib.cout(" -    0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F\n");
      for(int i=0; i<Disk.blockSize; i+=16)
      {
        if((i/16) < 16)
        {
          SysLib.cout(" ");
        }
        SysLib.cout(Integer.toHexString(i/16) + " | ");

        for(int j=0; j<16; j++)
        {
          temp = Integer.toHexString(buffer[i + j]);
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
            SysLib.cout(temp.substring(temp.length()-3,temp.length()-1) + " ");
          }
        }

        SysLib.cout (" | ");

        for(int j=0; j<16; j++)
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

        SysLib.cout("\n");
      }
    }
    SysLib.exit();
  }
}
