package com.ultikits.ultitools.utils;

import java.io.UnsupportedEncodingException;

public class TerminalProgressBarUtils {
    /**
     * 打印进度条（长度变化）
     *
     * @param length 长度
     * @throws InterruptedException 抛出异常
     */
    public static void printProgressBar(Integer length) throws InterruptedException, UnsupportedEncodingException {
        StringBuilder progress = new StringBuilder("Progress:|=");
        for (int i = 1; i <= length; i++) {
            if (i % 2 == 0) {
                progress.append("=");
            }
            System.out.print(progress.toString() + i + "%");
            Thread.sleep(100);
            for (int j = 0; j <= (progress.toString() + i).length(); j++) {
                System.out.print("\b");
            }
        }
        System.out.println();
    }

    /**
     * 打印进度条（长度固定）
     *
     * @param length 长度
     * @throws InterruptedException 抛出异常
     */
    public static void printProgressFixed(Integer length) throws InterruptedException {
        System.console().writer().print("Progress:|");
        for(int i = 1;i <= length;i++) {
            if (i % 2 == 0) {
                System.console().writer().print("=");
            }
            for(int k=0;k< (length/2 - i/2);k++) {
                System.console().writer().print(" ");
            }
            System.console().writer().print("|"+i + "%");
            Thread.sleep(100);
            for (int j = 0; j <= (length/2 - i/2+("|"+i).length()); j++) {
                System.console().writer().print("\b");
            }
        }
        System.console().writer().println();
    }
}
