package test;

import common.KeySelectorWrapper;
import common.MapKeyedState;
import function.KeySelector;
import function.MapFunction;
import function.ReduceFunction;
import io.BufferPool;
import io.InputChannel;
import main.KafkaSender;
import operator.OneInputStreamOperator;
import operator.StreamMap;
import operator.StreamReduce;
import record.StreamElement;
import record.StreamRecord;
import record.Tuple2;
import task.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author kevin.zeng
 * @description 按key分区的测试类
 * @create 2022-08-12
 */
public class TestPartition {
//    static int parrellism = Runtime.getRuntime().availableProcessors();
    static int mapParrellism = 2;
    static int reduceParrellism = 3;
    //TODO 相当于execute中的内容
    public static void main(String[] args) throws InterruptedException {
        //TODO 以下为DAG图构造过程（此处只用了硬代码）

        /*TODO 为处理Watermark等多种类型数据，
           BufferPool，InputChannel等数据结构存放类型可能要修改为数据基类StreamElement
         */

        //****连接source和map算子
        //1个source 2个map
        SourceStreamTask consumer = new SourceStreamTask();
        consumer.name("Source");

        //创建key选择器
        KeySelector<Tuple2<String,Integer>,String> keySelector = new WordKeySelector();
        //将key选择器转换为StreamElement包装的形式
        KeySelectorWrapper<Tuple2<String,Integer>> wrapper = new KeySelectorWrapper<>();
        KeySelector<StreamElement, String> keySelector1 = wrapper.convert(keySelector);
        //map算子的运行节点
        ExecutionJobVertex<String,Tuple2<String,Integer>> vertex = new ExecutionJobVertex<>();
        //创建map算子，声明key选择器
        OneInputStreamOperator<String, Tuple2<String, Integer>, MapFunction<String, Tuple2<String, Integer>>> mapper = new StreamMap<>(new Mapper());
        //运行节点中包含的运行实例task
        List<StreamTask<String,Tuple2<String,Integer>>> mapTaskList = new ArrayList<>();
        vertex.setTasks(mapTaskList);
        //source算子绑定下游输出Buffer
        BufferPool<StreamElement> sourceBuffer = new BufferPool<>();
        consumer.setOutput(sourceBuffer);
        //map算子的channel集合
        List<InputChannel<StreamElement>> mapChannel = new ArrayList<>();
        //channel输入集合要和上游task的buffer绑定，以实现并行
        sourceBuffer.bindInputChannel(mapChannel);
        //算子有几个并行度就会有几个bufferPool，输出数据类型为map
        List<BufferPool<StreamElement>> mapBuffer = new ArrayList<>();
        for (int i = 0; i < mapParrellism ; i++) {
            BufferPool<StreamElement> pool = new BufferPool<>();
            //map算子的bufferPool要启用分区，以便进行reduce计算
            pool.enablePartition();
            mapBuffer.add(pool);
        }
        //根据下游的并行度设置InputChannel，这里下游是map算子
        for (int i = 0; i < mapParrellism; i++) {
            //创建task
            OneInputStreamTask<String,Tuple2<String,Integer>> mapTask = new OneInputStreamTask<>();
            mapTask.setMainOperator(mapper);
            //创建task的上游输入channel
            InputChannel<StreamElement> input = new InputChannel<>();
            //channel数据的提供者是sourceBuffer
            input.bindProviderBuffer(Collections.singletonList(sourceBuffer));
            //为channel绑定所属的运行节点
            input.bindExecutionVertex(vertex);
            //输入管道和task绑定
            mapTask.setInput(input);
            //每个task绑定一个输出池
            mapTask.setOutput(mapBuffer.get(i));
            //设置线程名称
            mapTask.name("Map" + i);
            //每个task绑定key选择器，用于分发数据
            mapTask.setKeySelector(keySelector1);
            //放入管道集合
            mapChannel.add(input);
            //放入运行实例集合
            mapTaskList.add(mapTask);
        }


        //****连接map和reduce算子
        //创建reduce算子，一个task对应一个，
        //transformation保存并行度，有几个并行度创建几个算子，然后绑定到task
//        OneInputStreamOperator<Map<String, Integer>, Map<String, Integer>, ReduceFunction<Map<String, Integer>>> reducer = new StreamReduce<>(new Reducer());
        List<StreamReduce<Tuple2<String,Integer>>> reducerList = new ArrayList<>();
//        ReduceValueState<Map<String,Integer>> reduceValueState = new ReduceValueState<>();
        for (int i = 0; i < reduceParrellism; i++) {
            StreamReduce<Tuple2<String, Integer>> reducer = new StreamReduce<>(new Reducer(),keySelector);
//            reducer.setValueState(reduceKeyState);
            //按key分区后，对每个task绑定不同的算子，每个算子独占一个状态后端
            reducer.setValueState(new MapKeyedState<>(keySelector));
            reducerList.add(reducer);
        }
        //reduce算子的运行节点
        ExecutionJobVertex<Tuple2<String,Integer>,Tuple2<String,Integer>> reduceVertex = new ExecutionJobVertex<>();
        //运行节点中包含的运行实例task
        List<StreamTask<Tuple2<String,Integer>,Tuple2<String,Integer>>> reduceTaskList = new ArrayList<>();
        reduceVertex.setTasks(reduceTaskList);
        //reduce算子的channel集合
        List<InputChannel<StreamElement>> reduceChannel = new ArrayList<>();
        for (int i = 0; i < mapParrellism; i++) {
            //map的每一个buffer都绑定reduce算子的所有input管道，按哈希值%管道数进行shuffle
            mapBuffer.get(i).bindInputChannel(reduceChannel);
        }
        //
        //reduce算子的buffer集合
        List<BufferPool<StreamElement>> reduceBuffer = new ArrayList<>();
        //channel输入集合要和上游task的buffer绑定，以实现并行
//        sourceBuffer.bindInputChannel(mapChannel);
        //根据下游的并行度设置InputChannel
        for (int i = 0; i < reduceParrellism; i++) {
            //创建task
            OneInputStateStreamTask<Tuple2<String,Integer>> reduceTask = new OneInputStateStreamTask<>();
            reduceTask.setMainOperator(reducerList.get(i));
//            reduceTask.setMainOperator(reducerList.get(0));
            //创建task的上游输入channel
            InputChannel<StreamElement> input = new InputChannel<>();
            //channel数据的提供者
            input.bindProviderBuffer(mapBuffer);
            //为channel绑定所属的运行节点
            input.bindExecutionVertex(vertex);

            //输入管道和task绑定
            reduceTask.setInput(input);
            //每个task绑定一个输出池
            BufferPool<StreamElement> pool = new BufferPool<>();
            reduceBuffer.add(pool);
            reduceTask.setOutput(pool);
            //设置线程名称
            reduceTask.name("Reduce" + i);
            //放入管道集合
            reduceChannel.add(input);
            //放入运行实例集合
            reduceTaskList.add(reduceTask);
        }

        //连接reduce和sink算子
        SinkStreamTask<Tuple2<String,Integer>> sinkTask = new SinkStreamTask<>();
        //task命名
        sinkTask.name("Sink");
        //创建输入管道
        InputChannel<StreamElement> sinkInput = new InputChannel<>();
        sinkTask.setInput(sinkInput);
        //为sink的inputChannel绑定数据源buffer
        sinkInput.bindProviderBuffer(reduceBuffer);
        /*TODO 当前数据传输方式为 Buffer push数据 到InputChannel
                是否需要改成InputChannel 从 上游Buffer拉取数据
         */
        //为每一个上游buffer绑定输出到下游的channel
        for (int i = 0; i < reduceParrellism; i++) {
            reduceBuffer.get(i).bindInputChannel(Collections.singletonList(sinkInput));
        }


        //****开始运行
        //map算子
        for(StreamTask<String, Tuple2<String, Integer>> task : mapTaskList){
            task.start();
        }
        TimeUnit.SECONDS.sleep(1);
        //source算子
        consumer.start();
        TimeUnit.SECONDS.sleep(1);
        //reduce算子
        for(StreamTask<Tuple2<String,Integer>,Tuple2<String,Integer>> task : reduceTaskList){
            task.start();
        }
        TimeUnit.SECONDS.sleep(1);
        //kafka生产者
        KafkaSender sender = new KafkaSender();
        sender.start();
        //sink算子
        sinkTask.start();

        TimeUnit.SECONDS.sleep(90);
//        System.out.println(Thread.currentThread().getName() + " 【WordCount】 result: " + reduceValueState.value());
//        System.out.println(Thread.currentThread().getName() + " 【WordCount】 result: " + .value());

        for (int i = 0; i < reduceParrellism; i++) {
            System.out.println(Thread.currentThread().getName() + " 【WordCount】 result: " + reducerList.get(i).getValueState().get());
        }
    }
}

class Splitter implements MapFunction<String,Map<String,Integer>>{

    @Override
    public Map<String, Integer> map(String value) {
        Map<String,Integer> map = new HashMap<>();
        String[] str = value.split(" ");
        for (String s : str) {
            map.put(s,map.getOrDefault(s,0)+1);
        }
        return map;
    }
}

class Mapper implements MapFunction<String,Tuple2<String,Integer>>{

    @Override
    public Tuple2<String, Integer> map(String value) {
        Tuple2<String,Integer> result = new Tuple2<>();
        result.f0 = value;
        result.f1 = 1;
        return result;
    }
}

class Reducer implements ReduceFunction<Tuple2<String,Integer>>{

    @Override
    public Tuple2<String, Integer> reduce(Tuple2<String, Integer> value1, Tuple2<String, Integer> value2) {
        Tuple2<String,Integer> result = new Tuple2<>();
        result.f0 = value1.f0;
        result.f1 = value1.f1 + value2.f1;
        return result;
    }
}

//class Reducer implements ReduceFunction<Map<String,Integer>>{
//
//    @Override
//    public Map<String, Integer> reduce(Map<String, Integer> value1, Map<String, Integer> value2) {
//        Map<String,Integer> map = new HashMap<>(value1);
//        for(Map.Entry<String,Integer> entry : value2.entrySet()){
//            String word = entry.getKey();
//            int count = entry.getValue();
//            map.put(word,map.getOrDefault(word,0)+count);
//        }
//        return map;
//    }
//}

class WordKeySelector implements KeySelector<Tuple2<String,Integer>,String>{

    @Override
    public String getKey(Tuple2<String, Integer> value) {
        return value.f0;
    }
}
//实现对Map根据Key分区
//class WordKeySelector implements KeySelector<Map.Entry<String,Integer>,String>{
//
//    @Override
//    public String getKey(Map.Entry<String, Integer> value) {
//        return value.getKey();
//    }
//}