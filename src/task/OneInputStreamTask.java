package task;

import record.CheckPoint;
import record.StreamRecord;

import java.util.concurrent.TimeUnit;

/**
 * @author kevin.zeng
 * @description
 * @create 2022-08-12
 */

//用于处理诸如map、reduce等算子逻辑
public class OneInputStreamTask<IN,OUT> extends StreamTask<IN,OUT> {

    @Override
    public void run(){
        String name = Thread.currentThread().getName();
        while(true){
            //从InputChannel读取数据
            System.out.println(name + " read from InputChannel");
            StreamRecord<IN> inputData = input.take();
            //如果遇到barrier类型数据，从inputChannel获取消费偏移量
            if(inputData instanceof CheckPoint) {

            }
            //调用处理逻辑
            System.out.println(name + " processing ....");
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            StreamRecord<OUT> outputData = mainOperator.processElement(inputData);
            System.out.println(name + " process result: " + outputData);
            //放入当前Task的缓冲池，并推向下游
            output.push(outputData);
            System.out.println(name + " write into BufferPool");
        }
    }
}
