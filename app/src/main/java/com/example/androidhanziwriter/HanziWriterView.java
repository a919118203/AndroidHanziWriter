package com.example.androidhanziwriter;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

public class HanziWriterView extends View {
    private final int MODE_NORMAL = 0;
    private final int MODE_WRITER = 1;
    private final int MODE_ANIM = 2;
    private int mode = MODE_NORMAL;
    private static final String TAG = "HanziWriterView";

    private Paint mStrokePaint;  //汉字轮廓的笔
    private Paint mMedianPaint;  //汉字笔画的笔
    private HanziBean hanziBean;
    private int strokeIndex;                //写到第几笔/动画播放到第几画
    private int maxPointIndex;

    private Path userDrawPath = new Path(); //用户画的path
    private Paint userPaint;                //用户的笔
    private int userPaintStrokeWidth = 60; //用户的笔的宽度
    private int userPaintJudgmentRange = 120; //用户的笔的判定范围
    private boolean strokeOk;               //笔画对不对

    private AnimatorSet writerAnim;
    private Path animPath = new Path();
    private Paint animPaint;

    private int normalColor = Color.BLACK;
    private int backgroundColor = Color.BLACK;//背景的字的颜色
    private int writerColor = Color.BLUE;
    private int animColor = Color.GREEN;
    private int medianColor = Color.RED;

    private OnWriterEndListener onWriterEndListener;
    private OnStrokeWriterEndListener onStrokeWriterEndListener;
    private OnAnimStrokeWriterStartListener onAnimStrokeWriterStartListener;

    public HanziWriterView(Context context) {
        super(context);
    }

    public HanziWriterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HanziWriterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void init(){
        mStrokePaint = new Paint();
        mStrokePaint.setStrokeWidth(10);
        mStrokePaint.setStyle(Paint.Style.FILL);

        mMedianPaint = new Paint();
        mMedianPaint.setStrokeWidth(5);
        mMedianPaint.setColor(medianColor);
        mMedianPaint.setStyle(Paint.Style.STROKE);

        userPaint = new Paint();
        userPaint.setStrokeWidth(userPaintStrokeWidth);
        userPaint.setStyle(Paint.Style.STROKE);
        userPaint.setStrokeCap(Paint.Cap.ROUND);

        animPaint = new Paint();
        animPaint.setColor(animColor);
        animPaint.setStrokeWidth(120);
        animPaint.setStyle(Paint.Style.STROKE);
        animPaint.setStrokeCap(Paint.Cap.ROUND);

    }

    /*************************************************开放接口 START************************************/

    /**
     * 监听是否写完这个字
     * @param onWriterEndListener
     */
    public void setOnWriterEndListener(OnWriterEndListener onWriterEndListener) {
        this.onWriterEndListener = onWriterEndListener;
    }

    /**
     * 监听每一笔
     * @param onStrokeWriterEndListener
     */
    public void setOnStrokeWriterEndListener(OnStrokeWriterEndListener onStrokeWriterEndListener) {
        this.onStrokeWriterEndListener = onStrokeWriterEndListener;
    }

    /**
     * 监听动画的每一笔的开始
     * @param onAnimStrokeWriterStartListener
     */
    public void setOnAnimStrokeWriterStartListener(OnAnimStrokeWriterStartListener onAnimStrokeWriterStartListener) {
        this.onAnimStrokeWriterStartListener = onAnimStrokeWriterStartListener;
    }

    /**
     * 设置默认状态下的颜色
     * @param normalColor
     */
    public void setNormalColor(int normalColor) {
        this.normalColor = normalColor;
    }

    @Override
    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    /**
     * 设置动画的颜色
     * @param animColor
     */
    public void setAnimColor(int animColor) {
        this.animColor = animColor;
        animPaint.setColor(animColor);
    }

    /**
     * 设置写字的颜色
     * @param writerColor
     */
    public void setWriterColor(int writerColor) {
        this.writerColor = writerColor;
    }

    /**
     * 设置新的汉字
     * @param hanziBean
     */
    public void setHanziBean(HanziBean hanziBean) {
        this.hanziBean = hanziBean;
        invalidate();
    }

    public void toNormal(){
        if(writerAnim != null && writerAnim.isRunning()){
            writerAnim.cancel();
        }

        mode = MODE_NORMAL;

        strokeIndex = 0;

        invalidate();
    }

    /**
     * 开始写汉字模式
     */
    public void writerHanzi(){
        if(writerAnim != null && writerAnim.isRunning()){
            writerAnim.cancel();
        }

        mode = MODE_WRITER;

        strokeIndex = 0;

        invalidate();
    }

    /**
     * 开始动画
     */
    public void startAnim(){
        if(getVisibility() != VISIBLE){
            return ;
        }

        if(writerAnim != null){
            writerAnim.cancel();
        }

        mode = MODE_ANIM;

        strokeIndex = -1;

        hanziBean.initHanzi(getWidth(), getHeight());

        writerAnim = new AnimatorSet();

        //一笔一笔地播放动画
        Animator[] animators = new Animator[hanziBean.getStrokeCount()];
        int index = 0;
        for(final Path path : hanziBean.getMedianPaths()){
            ValueAnimator va = ValueAnimator.ofFloat(0, 1);
            va.setDuration(1000);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                PathMeasure pm;
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float value = (float) valueAnimator.getAnimatedValue();
                    if(pm == null){
                        pm = new PathMeasure(path, false);
                    }
                    float end = pm.getLength() * value ;
                    animPath.reset();
                    pm.getSegment(0, end, animPath, true);
                    invalidate();
                }
            });
            va.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    strokeIndex ++;
                    if(onAnimStrokeWriterStartListener != null){
                        onAnimStrokeWriterStartListener.onStart(strokeIndex);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animator) {

                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });

            if(index > 0){
                va.setStartDelay(500);
            }
            animators[index++] = va;
        }

        writerAnim.playSequentially(animators);
        writerAnim.start();
    }

    /*************************************************开放接口 END************************************/

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(hanziBean != null){
            hanziBean.initHanzi(getWidth(), getHeight());

            //画汉字的轮廓
            for(int i = 0; i < hanziBean.getStrokePaths().size() ; i ++){
                if(mode == MODE_NORMAL){
                    mStrokePaint.setColor(normalColor);
                }else{
                    if(i < strokeIndex){
                        mStrokePaint.setColor(mode == MODE_WRITER ? writerColor : animColor);
                    }else{
                        mStrokePaint.setColor(backgroundColor);
                    }
                }
                canvas.drawPath(hanziBean.getStrokePaths().get(i), mStrokePaint);
            }

            if(mode == MODE_WRITER && strokeIndex < hanziBean.getStrokeCount()){
                //画笔画
                canvas.drawPath(hanziBean.getMedianPaths().get(strokeIndex), mMedianPaint);

                //显示中线的所有点  用于调试  debug
//                for(List<Integer> points : hanziBean.getMedians().get(strokeIndex)){
//                    int x = hanziBean.getCoordinateX(points.get(0));
//                    int y = hanziBean.getCoordinateY(points.get(1));
//                    canvas.drawPoint(x, y, mMedianPaint);
//                }

                //当前画到第几画 就裁剪哪
                canvas.clipPath(hanziBean.getStrokePaths().get(strokeIndex));

                //画用户画的
                userPaint.setColor(writerColor);
                canvas.drawPath(userDrawPath, userPaint);
            }

            if(mode == MODE_ANIM && strokeIndex < hanziBean.getStrokeCount()){
                //当前画到第几画 就裁剪哪
                canvas.clipPath(hanziBean.getStrokePaths().get(strokeIndex));

                canvas.drawPath(animPath, animPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(mode != MODE_WRITER || strokeIndex >= hanziBean.getStrokeCount()){
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                userDrawPath.reset();
                invalidate();

                if(!inHand(x, y)){
                    return false;
                }

                strokeOk = true;
                maxPointIndex = 0;
                userDrawPath.moveTo(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                // 在开始和结束坐标间画一条线
                userDrawPath.lineTo(x, y);

                if(!checkDrawPointInHanziStroke(x, y)){
                    strokeOk = false;
                }

                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                userDrawPath.reset();
                //经过了80%的点  且  中间没有错误  就开始下一笔
                if(maxPointIndex >= (int)(hanziBean.getMedians().get(strokeIndex).size() * 0.8) && strokeOk){
                    if(onStrokeWriterEndListener != null){
                        onStrokeWriterEndListener.onEnd(strokeIndex);
                    }

                    //下一笔
                    strokeIndex ++;

                    //写完了
                    if(strokeIndex >= hanziBean.getStrokeCount() && onWriterEndListener != null){
                        onWriterEndListener.onEnd();
                    }
                }else{
                    if(!strokeOk){
                        Log.e(TAG, "onTouchEvent: 中途失败");
                    }else{
                        Log.e(TAG, "onTouchEvent: 结尾失败"
                                + maxPointIndex + " "
                                + hanziBean.getMedians().get(strokeIndex).size() + " "
                                + ((int)(hanziBean.getMedians().get(strokeIndex).size() * 0.8)));
                    }
                }

                invalidate();
                break;

        }
        return true;
    }

    /**
     * 检测用户画的这一点是否在笔画中心线的范围内
     * @param x0
     * @param y0
     * @return
     */
    private boolean checkDrawPointInHanziStroke(float x0,float y0){
        if(hanziBean == null){
            return false;
        }

        //对应笔画的坐标点
        List<List<Integer>> coordinates = hanziBean.getMedians().get(strokeIndex);

        //判断点到每条直线的距离是否足够小  并且垂点在定义域内
        List<Integer> lastCoordinate = null;
        boolean result = false;
        for(int i = coordinates.size() - 1 ; i >= 0 ; i --){
            List<Integer> coordinate = coordinates.get(i);

            if(lastCoordinate != null){
                int x1 = hanziBean.getCoordinateX(lastCoordinate.get(0));
                int y1 = hanziBean.getCoordinateY(lastCoordinate.get(1));
                int x2 = hanziBean.getCoordinateX(coordinate.get(0));
                int y2 = hanziBean.getCoordinateY(coordinate.get(1));

                //防止直线平行Y轴
                if(x1 == x2){
                    x2 ++;
                }

                //防止直线平行X轴
                if(y1 == y2){
                    y2++;
                }

                //Ax + By + C = 0
                double A = y2 - y1;
                double B = x1 - x2;
                double C = y1 * (x2 - x1) - x1 * (y2 - y1);

                //算出点到线的距离
                double d = Math.abs(
                        (A * x0 + B* y0 + C) /
                                Math.sqrt(A * A + B * B)
                );

                //圆和直线是否有交点
                if(d <= userPaintJudgmentRange / 2.0){
                    double r = userPaintJudgmentRange / 2.0;
                    double A1 = (B * B) / (A * A) + 1;
                    double B1 = (2 * B * C) / (A * A) + (2 * x0 * B) / A - 2 * y0;
                    double C1 = (C * C) / (A * A) + (2 * x0 * C) / A + x0 * x0 + y0 * y0 - r * r;

                    //b^2-4ac
                    double dt = B1 * B1 - 4 * A1 * C1;
                    double Y1 = (-B1 + Math.sqrt(dt)) / (2 * A1);
                    double Y2 = (-B1 - Math.sqrt(dt)) / (2 * A1);

                    double X1 = (-B * Y1 - C) / A;
                    double X2 = (-B * Y2 - C) / A;

                    double start1 = Math.min(x1, x2);
                    double end1 = Math.max(x1, x2);
                    double start2 = Math.min(X1, X2);
                    double end2 = Math.max(X1, X2);

                    //判断两区间是否有重叠的部分
                    if(Math.max(start1, start2) <= Math.min(end1, end2)){
                        result = true;
                        maxPointIndex = Math.max(i + 1, maxPointIndex);
                        break;
                    }
                }
            }
            lastCoordinate = coordinate;
        }

        return result;
    }

    /**
     * 用户现在画的点是否在笔画的头部
     * @return
     */
    private boolean inHand(double x0, double y0){
        if(hanziBean == null || strokeIndex >= hanziBean.getStrokeCount()){
            return false;
        }

        //获取对应笔画的第一个点的坐标
        int x = hanziBean.getCoordinateX(hanziBean.getMedians().get(strokeIndex).get(0).get(0));
        int y = hanziBean.getCoordinateY(hanziBean.getMedians().get(strokeIndex).get(0).get(1));

        double distance = getPointDistance(x0, y0, x, y);

        return distance <= userPaintJudgmentRange / 2.0;
    }

    /**
     * 计算两点间的距离
     * @return
     */
    private double getPointDistance(double x0, double y0, double x1, double y1){
        double x = x0 - x1;
        double y = y0 - y1;
        return Math.sqrt(x * x + y * y);
    }

    /**
     * 监听写完字
     */
    public interface OnWriterEndListener{
        void onEnd();
    }

    /**
     * 监听每一笔画写完的时候
     * index   第几笔
     */
    public interface OnStrokeWriterEndListener{
        void onEnd(int index);
    }

    /**
     * 监听动画开始写第几笔
     * index   开始写第几笔
     */
    public interface OnAnimStrokeWriterStartListener{
        void onStart(int index);
    }
}
