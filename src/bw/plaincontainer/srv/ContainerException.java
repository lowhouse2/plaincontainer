/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package bw.plaincontainer.srv;

/**
 * 容器操作异常类
 * @author user
 * 
 */
public class ContainerException extends Exception {
    public static final int SORT_FILEOPEN = 1;  //文件打开异常，如创建/打开
    public static final int SORT_CLOSE = 2; //文件关闭异常
    public static final int SORT_IGGLE = 3; //文件格式错误
    public static final int SORT_OPERATE = 4;   //文件操作错误
    public static final int SORT_OTHER = 5;    //other
    
    private int sort;   //异常类别
    private String msg; //错误信息
    
    public ContainerException(int sort, String r) {
        super(r);
        this.sort = sort;
        this.msg = r;
    }
    
    public ContainerException(int sort, String r, Exception ex){
        super(ex);
        this.sort = sort;
        this.msg = r;
    }

    /**
     * @return the sort
     */
    public int getSort() {
        return sort;
    }

    /**
     * @return the msg
     */
    public String getMsg() {
        return msg;
    }
    
}
