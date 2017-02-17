/*
 * Container.java 
 */

package bw.plaincontainer.bo;

import java.util.List;

/**
 * 容器对象
 * @author user
 */
public class Container {
    public static final int FORMAT_PLAIN = 0;   //无加密
    public static final int FORMAT_SIMPLE = 1;  //简单基于位置的XOR加密
    public static final int CONTAINER_VERSION = 1;  //容器版本
    public static final int HEAD_SIZE = 0x100;  //固定为256字节
    public static final int HEAD_STATIC_SIZE = 26;  //块头固定区域大小（除保留区域以外）
    public static final int HEAD_RESERVEDAREA_SIZE = HEAD_SIZE-HEAD_STATIC_SIZE; //
    
    private static String charset = "UTF-8";

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
    
    private byte[] flag = {0x43, 0x4f, 0x4e,0x54}; //容器标志CONT ，4B
    private int headSize = HEAD_SIZE;   //0x0100;   //容器头大小,暂固定为256字节 2B
    private int version = 1;    //容器版本 1B
    private int format = 0;     //容器格式 1B
    private byte[] param = new byte[16];    //容器参数，用于加密等
    private byte[] reserved = new byte[HEAD_RESERVEDAREA_SIZE];//256-26]; //保留区域大小
    private int headChkSum; //容器头校验和 2B
            
    
    private List<FileBlock> blocks; //文件块对象        //TODO 如何使用？换成当前和前一个？暂不使用，由上层应用处理缓存问题
    private List<Integer> positions;    //文件块开始位置    //TODO 与Operator重复
    
    public byte[] getHeadBytes(){
        int p = 0;
        byte[] hb = new byte[headSize];
        hb[p] = flag[p++];
        hb[p] = flag[p++];
        hb[p] = flag[p++];
        hb[p] = flag[p++];

        hb[p++] = (byte)(headSize&0x000000ff);
        hb[p++] = (byte)((headSize&0x0000ff00)>>8);
        hb[p++] = (byte)(version&0x000000ff);
        hb[p++] = (byte)(format&0x000000ff);
        System.arraycopy(param, 0, hb, p, param.length);
        p += param.length;
        System.arraycopy(reserved, 0, hb, p, reserved.length);
        p += reserved.length;
        int cs = 0;
        for (int i=0;i<p;i++){
            cs += hb[i];
        }
        hb[p++] = (byte) (cs & 0x000000ff);
        hb[p++] = (byte) ((cs & 0x0000ff00)>>8);
        return hb;
    }
    
    public String loadHead(byte[] data){
        int p;
        for (p=0;p<flag.length;p++){
            if (data[p] != flag[p]){
                return "文件头不符";
            }
        }
        headSize = (data[p++]&0x000000ff) | ((data[p++]&0x000000ff)<<8);
        version = data[p++];
        format = data[p++];
        for (int i=0;i<param.length;i++){
            param[i] = data[p+i]; 
        }
        p += param.length;
        reserved = new byte[HEAD_RESERVEDAREA_SIZE];//headSize-26];  
        for (int i=0;i<HEAD_RESERVEDAREA_SIZE;i++){
            reserved[i] = data[p+i];
        }
        p += HEAD_RESERVEDAREA_SIZE;
        int cs = 0;
        for (int i=0;i<p;i++){
            cs += data[i];
        }
        headChkSum = (data[p++]&0x000000ff) | ((data[p++]&0x000000ff)<<8);
        if ((cs&0x0000ffff)!= headChkSum){
            return "文件头校验和错误";
        }
        return null;
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
     * @return the version
     */
    public int getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * @return the format
     */
    public int getFormat() {
        return format;
    }

    /**
     * @param format the format to set
     */
    public void setFormat(int format) {
        this.format = format;
    }

    /**
     * @return the param
     */
    public byte[] getParam() {
        return param;
    }

    /**
     * @param param the param to set
     */
    public void setParam(byte[] param) {
        this.param = param;
    }

    /**
     * @return the reserved
     */
    public byte[] getReserved() {
        return reserved;
    }

    /**
     * @param reserved the reserved to set
     */
    public void setReserved(byte[] reserved) {
        this.reserved = reserved;
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
     * @return the blocks
     */
    public List<FileBlock> getBlocks() {
        return blocks;
    }

    /**
     * @param blocks the blocks to set
     */
    public void setBlocks(List<FileBlock> blocks) {
        this.blocks = blocks;
    }

    /**
     * @return the positions
     */
    public List<Integer> getPositions() {
        return positions;
    }

    /**
     * @param positions the positions to set
     */
    public void setPositions(List<Integer> positions) {
        this.positions = positions;
    }
    
}
