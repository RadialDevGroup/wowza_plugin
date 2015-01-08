package com.gloo;

public class Program {
	
    public String date;
    public Integer no;
    public String title;
    public String path;
    public Integer start;
    public Integer length;
    public Long progress;
    
    Program(String d, Integer n, String t, String p, Integer s, Integer l, Long pr) {
    	date = d;
    	no = n;
    	title = t;
    	path = p;
    	start = s;
    	length = l;
    	progress = pr;
    }

}
