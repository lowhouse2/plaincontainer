/*
 * ContainerOperator.java 2016.8.30
 */
package bw.plaincontainer.srv;

import bw.plaincontainer.bo.Container;
import bw.plaincontainer.bo.FileBlock;
import static bw.plaincontainer.bo.FileBlock.BLOCKSIZE_PARSEBUF_LEN;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 容器操作类
 *
 * @author Blue Water 首先使用类静态方法生成容器文件对应的操作对象，然后使用对象进行读写等以及关闭。
 * 容器静态方法：生成容器对应的操作对象，可进行对应读写操作 创建容器、追加内容、读取容器中内容（顺序迭代、随机、扫描） 1.创建容器：创建新容器用于追加
 * 2.打开容器用于追加内容：打开已有容器以向结尾追加文件内容。暂时不支持读操作，待将来实现。
 * 3.打开容器用于读取，包括打开用于顺序读和打开用于随机读，顺序读通过调用索引方法可进行随机读，或对已建立索引的内容进行随机读访问
 *
 *
 * 容器对象方法：对应容器打开操作类型可进行读写等操作 1.顺序迭代读取容器中下一内容 可不需要对容器预先全扫描，可提高启动速度。随着迭代读取过程可形成索引
 * 2.扫描：读取整个容器中内容头并生成索引，用于随机读取,可获取文件数，作为内部方法，由其它方法在需要时调用一次
 * 3.随机读取：使用扫描获取的索引信息随机读取容器中内容 4.追加内容：仅限追加方式打开的容器，将维护容器模式 5.关闭容器 容器内部保持容器读取专用指针
 *
 */
public class ContainerOperator {

    public static final int MODE_CLOSED = 0;
    public static final int MODE_NEW = 1;   //新创建的空容器，一旦追加内容后即变为append模式
    public static final int MODE_APPEND = 2;
    public static final int MODE_READ = 3;

    RandomAccessFile file;  //容器文件
    Container c;    //容器业务对象
    int mode = MODE_CLOSED; //容器状态
    //int blocks = 0; //容器中已有文件数，可由index.size()替代
    List<Long> index = new ArrayList<>();    //容器内文件块位置索引
    int pIndex;  //当前读取块索引序号
    boolean counted = false;    //已运行countBlock创建了文件全部索引的标记

    /**
     * 对象不能直接创建，必须通过类静态方法生成
     */
    private ContainerOperator() {
        super();
    }

    //类内静态方法，用于创建容器操作对象
    /**
     * 创建新容器 容器头仅在实际追加文件数据或关闭时才被保存到磁盘上。
     *
     * @param fileName
     * @return 对应新容器的操作类
     * @throws bw.plaincontainer.srv.ContainerException
     */
    public static ContainerOperator createContainer(String fileName) throws ContainerException {
        ContainerOperator co = new ContainerOperator();
        try {
            co.file = new RandomAccessFile(fileName, "rw");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_FILEOPEN, fileName, ex);

        }
        co.c = new Container();
        co.mode = ContainerOperator.MODE_NEW;
        return co;
    }

    /**
     * 打开已有容器用于追加文件
     *
     * @param fileName 容器路径
     * @return 容器操作对象
     * @throws ContainerException 打开错误
     */
    public static ContainerOperator appendContainer(String fileName) throws ContainerException {
        ContainerOperator co = new ContainerOperator();
        try {
            co.file = new RandomAccessFile(fileName, "rw");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_FILEOPEN, "打开文件用于追加");
        }
        //读取容器头
        co.c = new Container();
        byte[] buf = new byte[co.c.getHeadSize()];
        try {
            co.file.read(buf);
            co.countBlock();
            co.file.seek(co.file.length());
        } catch (IOException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_OPERATE, "读取", ex);
        }
        String r = co.c.loadHead(buf);
        if (r != null) {
            throw new ContainerException(ContainerException.SORT_IGGLE, r);
        }
        co.mode = ContainerOperator.MODE_APPEND;
        return co;
    }

    /**
     * 打开容器用于读取
     *
     * @param fileName 容器路径
     * @param random 是否以随机读方式打开容器文件
     * @return 容器操作对象
     * @throws ContainerException 如果打开容器过程发生错误，则爆出该异常
     */
    public static ContainerOperator readContainer(String fileName, boolean random) throws ContainerException {
        ContainerOperator co = new ContainerOperator();
        try {
            co.file = new RandomAccessFile(fileName, "r");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_FILEOPEN, "打开文件用于读取");
        }
        //读取容器头
        co.loadHead();
        if (random) {
            co.countBlock();
            try {
                co.file.seek(co.index.get(0));  //move to first
                //co.pIndex = 0;    //setted in loadHead()
            } catch (IOException ex) {
                Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
                throw new ContainerException(ContainerException.SORT_OPERATE, "seek 0", ex);
            }
        }
        co.mode = ContainerOperator.MODE_READ;
        return co;
    }

    //对象内方法，用于操作容器
    private void loadHead() throws ContainerException {
        //读取容器头
        c = new Container();
        byte[] buf = new byte[c.getHeadSize()];
        try {
            file.read(buf);
        } catch (IOException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_OPERATE, "读取", ex);
        }
        String r = c.loadHead(buf);
        if (r != null) {
            throw new ContainerException(ContainerException.SORT_IGGLE, r);
        }
        index.clear();
        //blocks = 0;
        try {
            index.add(file.getFilePointer());
        } catch (IOException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_IGGLE, r);
        }
        pIndex = 0;
    }

    /**
     * 快速扫描文件生成文件块计数和索引，用于加快容器操作
     *
     * @return 容器内文件数
     */
    private int countBlock() throws ContainerException {
        try {
            long p = c.getHeadSize();
            byte[] buf = new byte[FileBlock.BLOCKSIZE_PARSEBUF_LEN];//[10];
            FileBlock b = new FileBlock();

            file.seek(p);
            //循环，当未到文件结尾则读取块文件
            int n = 0;
            index.clear();
            //blocks = 0;

            do {
                n = file.read(buf);
                if (n == buf.length) {
                    //long size = (buf[6]&0x000000ff)&((buf[7]& 0x000000ff)<<8) &((buf[8]&0x000000ff)<<16) & ((buf[9]&0x000000ff)<<24); 
                    long size = b.loadBlockSize(buf);
                    index.add(p);
                    //blocks++;
                    p += size - buf.length;
                    file.seek(p);
                }
            } while (n == buf.length);
            counted = true; //完成统计
            System.out.println("countBlock:"+index.size());
            return index.size();//blocks;

        } catch (IOException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_OPERATE, "countBlock", ex);
        }

        //return index.size();//blocks;
    }

    /**
     * 向容器内添加文件
     *
     * @param blks 文件块对象List
     * @return 成功则返回添加文件数，否则抛出异常 9.28 包装IOException为ContainerException [161223]
     * @throws bw.plaincontainer.srv.ContainerException
     */
    public int append(List<FileBlock> blks) throws ContainerException {
        int n = 0;
        //MODE_NEW在进行一次追加操作后将转换为MODE_APPEND 
        if (mode == MODE_NEW) {
            try {
                //先保存文件头
                file.write(c.getHeadBytes());
            } catch (IOException ex) {
                Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
                throw new ContainerException(ContainerException.SORT_OPERATE, "SaveHead", ex);
            }
            mode = MODE_APPEND;
        }
        if (mode == ContainerOperator.MODE_APPEND) {
            //如果追加成功则维护blocks和index
            if (blks != null && !blks.isEmpty()) {
                try {
                    long len = file.length();
                    file.seek(len);
                    for (FileBlock b : blks) {
                        b.setBsn(index.size());//blocks++); //同时维护blocks
                        index.add(len);     //维护index索引
                        file.write(b.getBlockHead());
                        file.write(b.getContent());
                        int cs = b.getContentChkSum();
                        file.writeByte(cs & 0x000000ff);
                        file.writeByte((cs >> 8) & 0x000000ff);
                        len = file.getFilePointer();
                        n++;
                    }
                } catch (IOException ex) {
                    Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
                    throw new ContainerException(ContainerException.SORT_OPERATE, "ContainerOperator.append", ex);
                }
                return n;
            } else {
                throw new ContainerException(ContainerException.SORT_OTHER, "ContainerOperator.append:param blks is null or empty!");
            }

        } else {
            throw new ContainerException(ContainerException.SORT_FILEOPEN, "容器append");
        }
    }

    // 容器读操作：first,next,prev,index
    public FileBlock first() throws ContainerException {
        byte[] buf = new byte[FileBlock.BLOCKSIZE_PARSEBUF_LEN];
        //seek to block  0
        if (index.size() > 0) {
            try {
                //已索引，则直接移到索引指定位置(block 0 位置总是可以直接从index.get(0)获取到)
                file.seek(index.get(0));
            } catch (IOException ex) {
                Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
                throw new ContainerException(ContainerException.SORT_OPERATE, "Container.first() seek block 0", ex);
            }
        } else { //no fileblocks
            return null;
        }
        return next();
    }

    /**
     * 顺序读取容器中下一个块<br>
     * 读取上次读的块的下一个块，如该操作为打开容器后的首次读动作，则返回首块。
     * @return FileBlock ,如到达容器末尾
     * @throws ContainerException 
     */
    public FileBlock next() throws ContainerException {
        byte[] buf = new byte[FileBlock.BLOCKSIZE_PARSEBUF_LEN];
        try {
            if (file.getFilePointer() >= file.length()) {
                counted = true;
                return null;    //EOF
            }
        } catch (IOException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
        }

        //pIndex = 0;
        int n;
        try {
            n = file.read(buf);
            FileBlock fb = new FileBlock();
            if (n == buf.length) {
                //read content
                long size = fb.loadBlockSize(buf);
                byte[] buf2 = new byte[(int) (size - BLOCKSIZE_PARSEBUF_LEN)];
                file.read(buf2);
                fb.loadBlockBody(buf2);
                pIndex++;
                if (index.size() < pIndex) {
                    if (index.size() < pIndex - 1) {
                        throw new ContainerException(ContainerException.SORT_IGGLE, "ContainerOperator.first iggle index size:" + index.size() + ",pIndex:" + pIndex);
                    }
                    index.add(file.getFilePointer());
                }
                //return content
                return fb;
            } else {
                throw new ContainerException(ContainerException.SORT_IGGLE, "ContainerOperator.first loadBlockSize wanted:" + buf.length + " but got " + n);
            }
        } catch (IOException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_IGGLE, "ContainerOperator.first loadBlockData");
        }
    }
    /**
     * 随机读容器中文件块
     * @param n 块在容器中的位置索引，n从0开始
     * @return 文件块FileBlock对象
     * @throws ContainerException 如参数n超出范围，或文件操作出错，都会抛出该异常 
     */
    public FileBlock read(int n) throws ContainerException {
        //如
        if (index.size() <= n) {
            if (!counted) {
                countBlock();
            }
            if (index.size() <= n) {
                throw new ContainerException(ContainerException.SORT_OPERATE, "Container.read() 参数文件块序号n超出总块数");
            }
        }
        if (index.size() > n) {
            try {
                file.seek(index.get(n));
            } catch (IOException ex) {
                Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
                throw new ContainerException(ContainerException.SORT_OPERATE, "Container.read() seek block " + n, ex);
            }
            return next();
        }
        
        throw new ContainerException(ContainerException.SORT_OTHER,"Container.read()设计错误！ n:"+n+",index.size:"+index.size());
    }

    /**
     * 关闭容器文件
     */
    void close() throws ContainerException {
        try {
            file.close();
        } catch (IOException ex) {
            Logger.getLogger(ContainerOperator.class.getName()).log(Level.SEVERE, null, ex);
            throw new ContainerException(ContainerException.SORT_CLOSE, "ContainerOperator.close", ex);
        }

    }
}
