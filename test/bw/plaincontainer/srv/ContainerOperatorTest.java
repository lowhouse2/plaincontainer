/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bw.plaincontainer.srv;

import bw.plaincontainer.bo.FileBlock;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 测试方案： A：基本方案 1. 创建容器 2. 添加内容 3. 关闭容器 4. 打开容器读 5. 读取内容并比较 6. 关闭容器
 *
 * B：连续添加 1. 创建容器 2. 添加内容x1 3. 关闭容器 4. 追加方式打开 5. 连续追加内容x2 6. 关闭容器 7. 顺序读打开 8.
 * nextx2,first,read(1),read2 9. 关闭容器 10.重新随机读打开 11.read2 12.关闭
 *
 * @author remote
 */
public class ContainerOperatorTest {

    String rspath = "d:\\tmp\\resource\\";
    String outpath = "d:\\tmp\\";
    String[] bigpics = {"pb1.jpg", "pb2.jpg", "pb3.jpg", "pb4.jpg", "pb5.jpg"};
    String[] midpics = {"pm1.jpg", "pm2.jpg", "pm3.jpg", "pm4.jpg", "pm5.jpg"};
    String[] smallpics = {"ps1.jpg", "ps2.jpg", "ps3.jpg", "ps4.jpg", "ps5.jpg"};

    public ContainerOperatorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of createContainer method, of class ContainerOperator.
     */
    @Test
    public void testCreateContainer() throws Exception {
        System.out.println("createContainer");
        String fileName = "d:\\tmp\\create1.dat";
        ContainerOperator co = ContainerOperator.createContainer(fileName);
        assertNotNull(co);
        //append data
        File fi = new File(rspath + smallpics[0]);
        long len = fi.length();
        byte[] buf = new byte[(int) len];
        FileInputStream is = new FileInputStream(fi);
        int r = is.read(buf);
        System.out.println("读取文件，长度" + len + "字节,读取了" + r + "字节");
        assertEquals((int) len, r);
        is.close();
        FileBlock bc = new FileBlock(smallpics[0], "小文件测试1", buf);
        List<FileBlock> bs = new ArrayList<>();
        bs.add(bc);
        r = co.append(bs);
        assertEquals(r, 1);
        co.close();

        co = ContainerOperator.readContainer(fileName, false);
        assertNotNull(co);
        FileBlock rb = co.next();
        System.out.println("read from container, file name:" + rb.getFileName() + " Remark:" + rb.getRemark() + " Size:" + rb.getContent().length);
        File fo = new File(outpath + "out-" + rb.getFileName());
        FileOutputStream os = new FileOutputStream(fo);
        os.write(rb.getContent());
        os.close();
    }

    /**
     * Test of createContainer and random read method, of class
     * ContainerOperator.
     */
    @Test
    public void testRandomeReadContainer() throws Exception {
        System.out.println("RandomeReadContainer");
        String fileName = "d:\\tmp\\create2.dat";
        ContainerOperator co = ContainerOperator.createContainer(fileName);
        assertNotNull(co);
        //append data
        List<FileBlock> bs = new ArrayList<>();
        int r;
        for (int i = 0; i < midpics.length; i++) {
            File fi = new File(rspath + midpics[i]);
            long len = fi.length();
            byte[] buf = new byte[(int) len];
            FileInputStream is = new FileInputStream(fi);
            r = is.read(buf);
            System.out.println("读取文件，长度" + len + "字节,读取了" + r + "字节");
            assertEquals((int) len, r);
            is.close();
            FileBlock bc = new FileBlock(midpics[i], "中等文件测试" + (i + 1), buf);

            bs.add(bc);
        }
        r = co.append(bs);
        assertEquals(5, r);
        co.close();

        //连续读3个文件，然后随机读第2个，再随机读第5个
        co = ContainerOperator.readContainer(fileName, true);
        assertNotNull(co);
        FileBlock rb;
        File fo;
        FileOutputStream os;
        for (int i = 0; i < 3; i++) {
            rb = co.next();
            System.out.println("read from container, file name:" + rb.getFileName() + " Remark:" + rb.getRemark() + " Size:" + rb.getContent().length);
            fo = new File(outpath + "out-" + rb.getFileName());
            os = new FileOutputStream(fo);
            os.write(rb.getContent());
            os.close();
        }
        rb = co.read(1);
        write2File(rb,outpath);
        
        rb = co.read(4);
        write2File(rb,outpath);

    }

    private void write2File(FileBlock block, String path) throws FileNotFoundException, IOException{
        File file = new File(path+"out-"+block.getFileName());
        FileOutputStream os = new FileOutputStream(file);
        os.write(block.getContent());
        os.close();
    }
    
    @Test
    public void testByteCompute() {
        System.out.println("test Byte compute");
        byte[] bd = {0x00, 0x01};
        int r1 = bd[1] & 0x0ff;
        int r2 = r1 << 8;
        int r3 = (bd[0] & 0x0ff) | ((bd[1] & 0x0ff) << 8);
        System.out.println("r2=" + r2 + ", r3=" + r3);
        assertEquals(r2, 256);
        assertEquals(r2, r3);

    }

    /**
     * Test of appendContainer method, of class ContainerOperator.
     */
    //@Test
    public void testAppendContainer() throws Exception {
        System.out.println("appendContainer");
        String fileName = "d:\\tmp\\append.dat";

        ContainerOperator result = ContainerOperator.createContainer(fileName);
        result.close();
        ContainerOperator.appendContainer(fileName);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of readContainer method, of class ContainerOperator.
     */
//    @Test
    public void testReadContainer() throws Exception {
        System.out.println("readContainer");
        String fileName = "";
        boolean random = false;
        ContainerOperator expResult = null;
        ContainerOperator result = ContainerOperator.readContainer(fileName, random);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of append method, of class ContainerOperator.
     */
    //   @Test
    public void testAppend() throws Exception {
        System.out.println("append");
        List<FileBlock> blks = null;
        ContainerOperator instance = null;
        int expResult = 0;
        int result = instance.append(blks);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of first method, of class ContainerOperator.
     */
//    @Test
    public void testFirst() throws Exception {
        System.out.println("first");
        ContainerOperator instance = null;
        FileBlock expResult = null;
        FileBlock result = instance.first();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of next method, of class ContainerOperator.
     */
    //@Test
    public void testNext() throws Exception {
        System.out.println("next");
        ContainerOperator instance = null;
        FileBlock expResult = null;
        FileBlock result = instance.next();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of read method, of class ContainerOperator.
     */
    //@Test
    public void testRead() throws Exception {
        System.out.println("read");
        int n = 0;
        ContainerOperator instance = null;
        FileBlock expResult = null;
        FileBlock result = instance.read(n);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of close method, of class ContainerOperator.
     */
    //@Test
    public void testClose() throws Exception {
        System.out.println("close");
        ContainerOperator instance = null;
        instance.close();
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}
