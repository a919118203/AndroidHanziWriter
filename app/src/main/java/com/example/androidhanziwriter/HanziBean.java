package com.example.androidhanziwriter;

import android.graphics.Path;
import android.text.TextUtils;

import androidx.core.graphics.PathParser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * ver1.1.3
 * 新的汉字详情
 * 包括笔画的路径和汉字外框的路径
 */
public class HanziBean {
    private String medianData;
    private int strokeCount;//笔画数
    private String word;
    private List<String> strokeData;

    private List<List<List<Integer>>> medians;//汉字中线
    private List<Path> strokePaths = new ArrayList<>();//汉字外框
    private Path strokePath = new Path();//汉字外框 全部合成一个path
    private List<Path> medianPaths = new ArrayList<>();//笔画
    private Path medianPath = new Path();//笔画 全部合成一个path
    private int width,height;//坐标基准的宽高

    public List<Path> getStrokePaths() {
        return strokePaths;
    }

    public Path getStrokePath() {
        return strokePath;
    }

    public List<Path> getMedianPaths() {
        return medianPaths;
    }

    public Path getMedianPath() {
        return medianPath;
    }

    /**
     * 通过给定的宽高，将汉字原本的坐标转换成合适的坐标
     *
     * 关于坐标
     * 此字符每个笔划的SVG路径数据列表，按正确的笔划顺序排序。每个笔划均以1024x1024大小的坐标系进行布局，其中：
     *
     * 左上角在位置（0，900）。
     * 右下角在位置（1024，-124）。
     * 请注意，当您向下移动时，y轴会下降，这是很危险的！为了正确显示这些路径，您应该按以下方式隐藏渲染它们：
     *
     * <svg viewBox="0 0 1024 1024">
     *   <g transform="scale(1, -1) translate(0, -900)">
     *     <path d="STROKE[0] DATA GOES HERE"></path>
     *     <path d="STROKE[1] DATA GOES HERE"></path>
     *     ...
     *   </g>
     * </svg>
     * @param width
     * @param height
     */
    public void initHanzi(int width, int height){
        if(this.width == width && this.height == height){
            return;
        }

        this.width = width;
        this.height = height;

        try{
            initStrokes(strokeData);
            initMedians(getMedians());
        }catch (Exception e){
            strokePaths.clear();
            strokePath.reset();
            medianPaths.clear();
            medianPath.reset();
        }
    }

    /**
     * 将strokes中的svg指令转换成path
     * @param strokes
     */
    private void initStrokes(List<String> strokes){
        strokePaths.clear();
        strokePath.reset();
        for(String svg : strokes){
            Path path = PathParser.createPathFromPathData(coordinateConversion(svg));
            strokePaths.add(path);
            strokePath.addPath(path);
        }
    }

    /**
     * 将medians中每个笔画的坐标转换成path
     * @param medians
     */

    private void initMedians(List<List<List<Integer>>> medians){
        medianPaths.clear();
        medianPath.reset();
        for(List<List<Integer>> median : medians){
            Path path = null;
            for(List<Integer> coordinate : median){
                if(path == null){
                    path = new Path();
                    path.moveTo(
                            getCoordinateX(coordinate.get(0)),
                            getCoordinateY(coordinate.get(1)));
                }else{
                    path.lineTo(
                            getCoordinateX(coordinate.get(0)),
                            getCoordinateY(coordinate.get(1)));
                }
            }
            medianPaths.add(path);
            medianPath.addPath(path);
        }
    }

    /**
     * 将svg中的坐标转换成正确的坐标
     * @return
     */
    private String coordinateConversion(String svg){
        StringBuilder sb = new StringBuilder();

        String[] options = parseSvg(svg);
        int numberIndex = 0;
        String curOption = "";

        //记录最后一点的坐标  如果svg命令是小写，就意味着他后面的数字是相对坐标   相对于前一个操作的坐标
        double offsetX = 0;
        double offsetY = 0;

        for(String option : options){
            char firstChar = option.charAt(0);
            if(firstChar >= '0' && firstChar <= '9' || firstChar == '-'){
                numberIndex ++;
                double value = Double.parseDouble(option);

                if("H".equals(curOption)){
                    sb.append(getCoordinateX(value));
                    offsetX = value;
                }else if("h".equals(curOption)){
                    sb.append(getCoordinateX(value + offsetX) - getCoordinateX(offsetX));
                    offsetX += value;
                }else if("V".equals(curOption)){
                    sb.append(getCoordinateY(value));
                    offsetY = value;
                }else if("v".equals(curOption)){
                    sb.append(getCoordinateY(value + offsetY) - getCoordinateY(offsetY));
                    offsetY += value;
                } else{
                    if(numberIndex % 2 == 0){
                        if(curOption.charAt(0) >= 'a' && curOption.charAt(0) <= 'z'){
                            sb.append(getCoordinateY(value + offsetY) - getCoordinateY(offsetY));
                            offsetY += value;
                        }else{
                            sb.append(getCoordinateY(value));
                            offsetY = value;
                        }
                    }else{
                        if(curOption.charAt(0) >= 'a' && curOption.charAt(0) <= 'z'){
                            sb.append(getCoordinateX(value + offsetX) - getCoordinateX(offsetX));
                            offsetX += value;
                        }else{
                            sb.append(getCoordinateX(value));
                            offsetX = value;
                        }
                    }
                }
            }else{
                numberIndex = 0;
                curOption = option;
                sb.append(option);
            }
            sb.append(" ");
        }

        if(sb.length() != 0){
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * 将svg字符串分割成命令+空格+数字的形式
     * @return
     */
    private String[] parseSvg(String svg){
        List<String> result = new ArrayList<>();
        StringBuilder numSb = new StringBuilder();
        for(int i = 0 ; i < svg.length() ; i ++){
            char c = svg.charAt(i);
            if(c >= '0' && c <= '9'){
                numSb.append(c);
            }else{
                if(c == '.'){
                    numSb.append(c);
                }else{
                    if(numSb.length() != 0){
                        result.add(numSb.toString());
                        numSb.delete(0, numSb.length());
                    }

                    if(c >= 'a' && c <='z' || c >= 'A' && c <= 'Z'){
                        result.add(String.valueOf(c));
                    }else if(c == '-'){
                        numSb.append(c);
                    }
                }
            }
        }

        return result.toArray(new String[]{});
    }

    /**
     * 获取转化过的x轴坐标
     * @return
     */
    public int getCoordinateX(double x){
        double beishu = width / 1024f;
        return (int) (x * beishu);
    }

    /**
     * 获取转化过的y轴坐标
     * @return
     */
    public int getCoordinateY(double y){
        double beishu = height / 1024f;
        return (int) ((- (y - 900)) * beishu);
    }

    /**
     * 获取汉字的所有笔画的中线坐标列表
     * 将字符串转换为三维int列表
     * 因为服务器返回的时候是个字符串
     * @return
     */
    public List<List<List<Integer>>> getMedians() {
        if(medians == null){
            if(!TextUtils.isEmpty(medianData)){
                medians = new Gson().fromJson(medianData,
                        new TypeToken<List<List<List<Integer>>>>(){}.getType());
            }else{
                medians = new ArrayList<>(0);
            }
        }
        return medians;
    }

    /**
     * 获取总共有多少画
     * @return
     */
    public int getStrokeCount(){
        return Math.min(getStrokeData().size(), getMedians().size());
    }

    public void setStrokeCount(int strokeCount) {
        this.strokeCount = strokeCount;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public List<String> getStrokeData() {
        return strokeData;
    }

    public void setStrokeData(List<String> strokeData) {
        this.strokeData = strokeData;
    }
}
