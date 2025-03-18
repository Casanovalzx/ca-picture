package com.ca.capicturebackend.utils;

/**
 * 工具类：颜色转换
 */
public class ColorTransfromUtils {

    public ColorTransfromUtils() {
        // 工具类不需要实例化
    }

    public static String getStandardColor(String color) {
        if(color.length() == 7) {
            color = color.substring(0,4) + "0" + color.substring(4,7);
        }
        return color;
    }
}
