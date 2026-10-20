package com.hc.drown.common.util;

import cn.hutool.core.util.StrUtil;

/**
 * 空间地理位置工具类
 */
public class GeoUtils {

    /**
     * 将经纬度转换为 WKT Point 格式: POINT(lng lat)
     */
    public static String toWktPoint(Double lng, Double lat) {
        if (lng == null || lat == null) {
            return null;
        }
        return String.format("POINT(%f %f)", lng, lat);
    }

    /**
     * 从 WKT Point 格式解析经纬度. 例如: POINT(113.2644 23.1291)
     */
    public static double[] parseWktPoint(String wkt) {
        if (StrUtil.isBlank(wkt)) {
            return null;
        }
        try {
            String content = StrUtil.subBetween(wkt, "(", ")");
            if (StrUtil.isBlank(content)) {
                return null;
            }
            String[] parts = content.trim().split("\\s+");
            if (parts.length >= 2) {
                double lng = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                return new double[]{lng, lat};
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }

    /**
     * 计算两个经纬度坐标之间的大圆距离（单位：米），使用 Haversine 公式
     */
    public static double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        final int R = 6371000; // 地球半径，单位米
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 从 WKT Polygon 格式解析坐标点集合. 例如: POLYGON((120.01 28.01, 120.02 28.02, ...))
     */
    public static java.util.List<double[]> parsePolygon(String wkt) {
        java.util.List<double[]> coords = new java.util.ArrayList<>();
        if (StrUtil.isBlank(wkt) || !wkt.toUpperCase().startsWith("POLYGON")) {
            return coords;
        }
        try {
            int start = wkt.indexOf("((");
            int end = wkt.indexOf("))");
            if (start == -1 || end == -1) {
                start = wkt.indexOf("(");
                end = wkt.indexOf(")");
            }
            if (start != -1 && end != -1) {
                String coordStr = wkt.substring(start + 2, end);
                String[] points = coordStr.split(",");
                for (String p : points) {
                    String[] parts = p.trim().split("\\s+");
                    if (parts.length >= 2) {
                        double lng = Double.parseDouble(parts[0]);
                        double lat = Double.parseDouble(parts[1]);
                        coords.add(new double[]{lng, lat});
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败
        }
        return coords;
    }

    /**
     * 射线投射法 (Ray Casting / PNPOLY) 判断点是否在多边形内部
     */
    public static boolean isPointInPolygon(double lng, double lat, java.util.List<double[]> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return false;
        }
        int i, j;
        boolean c = false;
        int nvert = polygon.size();
        for (i = 0, j = nvert - 1; i < nvert; j = i++) {
            double[] pi = polygon.get(i);
            double[] pj = polygon.get(j);
            if ((((pi[1] <= lat) && (lat < pj[1])) || ((pj[1] <= lat) && (lat < pi[1]))) &&
                (lng < (pj[0] - pi[0]) * (lat - pi[1]) / (pj[1] - pi[1]) + pi[0])) {
                c = !c;
            }
        }
        return c;
    }
}
