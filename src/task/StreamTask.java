package task;

import environment.RunTimeEnvironment;
import io.BufferPool;
import io.InputChannel;
import operator.StreamOperator;
import record.StreamRecord;

/**
 * @author kevin.zeng
 * @description 算子的执行实例
 * @create 2022-08-12
 */
public class StreamTask<IN,OUT> extends Thread {

    //task属于一个运行环境
    protected RunTimeEnvironment environment;


    //当前task生产的数据放到Buffer中
    protected BufferPool<StreamRecord<OUT>> output;

    //一个task接收一个InputChannel发送的数据
    protected InputChannel<StreamRecord<IN>> input;

    //task执行算子逻辑
    protected StreamOperator<IN,OUT> mainOperator;

    //task所属的外层节点
    protected ExecutionJobVertex vertex;

    public StreamTask(){
    }

    public void setMainOperator(StreamOperator<IN,OUT> mainOperator){
        this.mainOperator = mainOperator;
    }

    public void setOutput(BufferPool<StreamRecord<OUT>> output) {
        this.output = output;
    }

    public void setInput(InputChannel<StreamRecord<IN>> input) {
        this.input = input;
    }

    //设置线程名
    public void name(String name){
        super.setName(name);
    }
}