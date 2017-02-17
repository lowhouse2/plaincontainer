/*
 * Copyright Blue Water
 * 
 */

package bw.plaincontainer.bo;

import bw.plaincontainer.srv.ContainerException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 容器内存放的文件块
 * @author blue water
 * 使用方法：
 * 构造：使用参数构造方法或set设置属性
 * 保存：首先设置bsn，然后分别获取块头getBlockHead和文件体的字节流getContent，以及文件校验和getContentChkSum，保存
 * 读取：从文件流中解析并生成文件块信息
 *  TODO 开始需要先解析出文件块大小，因此需要分两步：
 *  1.解析读取文件块大小
 *  2.根据大小获取并解析文件块内容
 * 
 */
public class FileBlock {
    private static final int HEAD_SIZE_A = 17;  //块头固定部分长度
    public static final int BLOCKSIZE_PARSEBUF_LEN = 10;    //解析块大小的缓冲区长度
    private static String charset = "UTF-8";
    private byte[] flag = {0x42,0x4c,0x4f,0x43}; //块头标志BLOC，4B
    private int bsn;    //块序号 2B @4
    private int blockSize;  //块总大小 4B (包括块头标志和块序号) @6
    private int headSize; //块头长度，字节 2B (固定长度17+nameLength+remarkLength) @10
    private int nameLength; //文件名长度，1B @12
    private String fileName;    //文件名（UTF8编码） @13
    private int remarkLength;   //注释字节长度 2B  @13+文件名长度
    private String remark;  //注释（UTF8编码） @13+文件名长度+2
    private int headChkSum; //块头校验和 2B (不包括flag和bsn) @+注释长度
    private byte[] content; //文件内容字节序列，长度为 blockSize-headSize-2
    private int contentChkSum; //文件内容校验和 2B
    
    public FileBlock(){
        super();
    }
    public FileBlock(String fileName, String remark, byte[] content){
        this();
        this.fileName =  fileName;
        this.remark = remark;
        //this.content = content;
        this.setContent(content);
    }
    
    public byte[] getBlockHead(){
        try {
            byte[] fn = fileName.getBytes(charset);
            byte[] rm = remark.getBytes(charset);
            nameLength = fn.length;
            if (nameLength>255) nameLength = 255;   //文件名长度限定255字节以内
            remarkLength = rm.length;
            if (remarkLength > 65535) remarkLength = 65535; //注释长度限定65535字节以内
            headSize = HEAD_SIZE_A + nameLength+remarkLength;
            blockSize = headSize+content.length+2;
            byte[] head = new byte[headSize];
            int idx;
            for(idx=0;idx<flag.length;idx++){
                head[idx] = flag[idx];
            }
            head[idx++] = (byte) (bsn & 0x000000ff); 
            head[idx++] = (byte) ((bsn & 0x0000ff00)>>8);
            
            head[idx++] = (byte) (blockSize & 0x000000ff);
            head[idx++] = (byte) ((blockSize & 0x0000ff00)>>8);
            head[idx++] = (byte) ((blockSize & 0x00ff0000)>>16);
            head[idx++] = (byte) ((blockSize & 0xff000000)>>24);
            
            head[idx++] = (byte) (headSize & 0x000000ff);
            head[idx++] = (byte) ((headSize & 0x0000ff00)>>8);

            head[idx++] = (byte) (nameLength & 0x000000ff);
            System.arraycopy(fn, 0, head, idx, nameLength);
            idx += nameLength;
            head[idx++] = (byte) (remarkLength & 0x000000ff);
            head[idx++] = (byte) ((remarkLength & 0x0000ff00)>>8);
            System.arraycopy(rm, 0, head, idx, remarkLength);
            idx += remarkLength;
            int cs = 0;
            for (int i=BLOCKSIZE_PARSEBUF_LEN;i<idx; i++){
                cs += head[i];
            }
            head[idx++] = (byte) (cs & 0x000000ff);
            head[idx++] = (byte) ((cs & 0x0000ff00)>>8);
            
            return head;
            
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(FileBlock.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private String errMsg;  //错误信息
    
    /**
     * 解析文件块尺寸
     * @param blk 包含文件块开头大小属性的字节数组
     * @return 块大小,loadBlockBody获取内容字节数为 (块大小-BLOCKSIZE_PARSEBUF_LEN)
     * @throws ContainerException 
     */
    public long loadBlockSize(byte[] blk) throws ContainerException{
        if (blk.length<BLOCKSIZE_PARSEBUF_LEN){
            return 0;
        }
        int idx = 0;
        for (;idx<flag.length;idx++){
            if (blk[idx] != flag[idx]){
                errMsg = "块头不符！";
                throw new ContainerException(ContainerException.SORT_IGGLE,errMsg);
            }
        }
        bsn = (blk[idx++]&0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
        blockSize =  (blk[idx++]&0x000000ff) | ((blk[idx++]&0x000000ff)<<8) 
                | ((blk[idx++]&0x000000ff)<<16) | ((blk[idx++]&0x000000ff)<<24);
        return blockSize;
    }
    
    
    /**
     * 解析文件块除BlockSize以后内容,在loadBlockSize后执行
     * @param blk 包含BlockSize以后的块内容
     * @throws ContainerException 
     */
    public void loadBlockBody(byte[] blk) throws ContainerException{
        int idx = 0;
        headSize = (blk[idx++] & 0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
        nameLength = blk[idx++] & 0x000000ff;
        try {
            fileName = new String(blk, idx, nameLength, charset);
            idx+= nameLength;
            remarkLength = (blk[idx++] & 0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
            remark = new String(blk, idx, remarkLength, charset);
            idx += remarkLength;
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(FileBlock.class.getName()).log(Level.SEVERE, null, ex);
            errMsg = "文件名/注释解析错误";
            throw new ContainerException(ContainerException.SORT_IGGLE,errMsg);
        }
        int cs=0;
        for (int i=0;i<idx;i++){
            cs += blk[i];
        }
        headChkSum = (blk[idx++] & 0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
        if ((cs&0x0000ffff)!=headChkSum){
            errMsg = "块头"+bsn+"校验和错误";
            throw new ContainerException(ContainerException.SORT_IGGLE,errMsg);
        }
        int contentLen = blockSize-headSize-2;
        content = new byte[contentLen];
        //System.arraycopy(blk, 0, head, idx, remarkLength);
        for (int i=0;i<contentLen;i++){
            content[i] = blk[headSize - BLOCKSIZE_PARSEBUF_LEN + i];
        }
        cs = 0;
        contentChkSum = (blk[blockSize - BLOCKSIZE_PARSEBUF_LEN -2]&0x000000ff)|((blk[blockSize - BLOCKSIZE_PARSEBUF_LEN-1]&0x000000ff)<<8);
        for (int i=0;i<content.length;i++){
            cs += content[i];
        }
        if ((cs&0x0000ffff)!= contentChkSum){
            errMsg = "块"+bsn+"内容校验和错误";
            throw new ContainerException(ContainerException.SORT_IGGLE,errMsg);
        }
    }
    
    /**
     * 将fileblock字节数组解析转换为FileBlock对象属性
     * @param blk fleBlock字节流，可以是文件中读入或映射的字节数组
     * @return null：成功，或错误信息
     */
    public String loadBlockAll(byte[] blk){
        int idx = 0;
        for (;idx<flag.length;idx++){
            if (blk[idx] != flag[idx]){
                errMsg = "块头不符！";
                return errMsg;
            }
        }
        bsn = (blk[idx++]&0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
        blockSize =  (blk[idx++]&0x000000ff) | ((blk[idx++]&0x000000ff)<<8) 
                | ((blk[idx++]&0x000000ff)<<16) | ((blk[idx++]&0x000000ff)<<24);
        headSize = (blk[idx++] & 0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
        nameLength = blk[idx++] & 0x000000ff;
        try {
            fileName = new String(blk, idx, nameLength, charset);
            idx+= nameLength;
            remarkLength = (blk[idx++] & 0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
            remark = new String(blk, idx, remarkLength, charset);
            idx += remarkLength;
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(FileBlock.class.getName()).log(Level.SEVERE, null, ex);
            errMsg = "文件名/注释解析错误";
            return errMsg;
        }
        headChkSum = (blk[idx++] & 0x000000ff) | ((blk[idx++]&0x000000ff)<<8);
        int cs=0;
        for (int i=BLOCKSIZE_PARSEBUF_LEN;i<idx;i++){
            cs += blk[i];
        }
        if ((cs&0x0000ffff)!=headChkSum){
            errMsg = "块头"+bsn+"校验和错误";
            return errMsg;
        }
        content = new byte[blockSize-headSize-2];
        //System.arraycopy(blk, 0, head, idx, remarkLength);
        for (int i=0;i<blockSize-headSize-2;i++){
            content[i] = blk[headSize+i];
        }
        cs = 0;
        contentChkSum = (blk[blockSize-2]&0x000000ff)|((blk[blockSize-1]&0x000000ff)<<8);
        for (int i=0;i<content.length;i++){
            cs += content[i];
        }
        if ((cs&0x0000ffff)!= contentChkSum){
            errMsg = "块"+bsn+"内容校验和错误";
            return errMsg;
        }
        return null;
    }   

    /**
     * @return the charset
     */
    public static String getCharset() {
        return charset;
    }

    /**
     * @param aCharset the charset to set
     */
    public static void setCharset(String aCharset) {
        charset = aCharset;
    }

    /**
     * @return the flag
     */
    public byte[] getFlag() {
        return flag;
    }

    /**
     * @param flag the flag to set
     */
    public void setFlag(byte[] flag) {
        this.flag = flag;
    }

    /**
     * @return the bsn
     */
    public int getBsn() {
        return bsn;
    }

    /**
     * @param bsn the bsn to set
     */
    public void setBsn(int bsn) {
        this.bsn = bsn;
    }

    /**
     * @return the blockSize
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * @param blockSize the blockSize to set
     */
    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    /**
     * @return the headSize
     */
    public int getHeadSize() {
        return headSize;
    }

    /**
     * @param headSize the headSize to set
     */
    public void setHeadSize(int headSize) {
        this.headSize = headSize;
    }

    /**
     * @return the nameLength
     */
    public int getNameLength() {
        return nameLength;
    }

    /**
     * @param nameLength the nameLength to set
     */
    public void setNameLength(int nameLength) {
        this.nameLength = nameLength;
    }

    /**
     * @return the fileName
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @param fileName the fileName to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * @return the remarkLength
     */
    public int getRemarkLength() {
        return remarkLength;
    }

    /**
     * @param remarkLength the remarkLength to set
     */
    public void setRemarkLength(int remarkLength) {
        this.remarkLength = remarkLength;
    }

    /**
     * @return the remark
     */
    public String getRemark() {
        return remark;
    }

    /**
     * @param remark the remark to set
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * @return the headChkSum
     */
    public int getHeadChkSum() {
        return headChkSum;
    }

    /**
     * @param headChkSum the headChkSum to set
     */
    public void setHeadChkSum(int headChkSum) {
        this.headChkSum = headChkSum;
    }

    /**
     * @return the content
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    private void setContent(byte[] content) {
        this.content = content;
        contentChkSum = 0;
        for (byte c:content){
            contentChkSum += c;
        }
    }


    /**
     * @return the contentChkSum
     */
    public int getContentChkSum() {
        return contentChkSum;
    }

    /**
     * @param contentChkSum the contentChkSum to set
     */
    public void setContentChkSum(int contentChkSum) {
        this.contentChkSum = contentChkSum;
    }

    /**
     * @return the errMsg
     */
    public String getErrMsg() {
        return errMsg;
    }
}
