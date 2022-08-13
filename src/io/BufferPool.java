package io;


import function.KeySelector;
import record.StreamElement;
import record.StreamRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author kevin.zeng
 * @description Task生产数据后放入Buffer，每个Buffer对应一个Task
 * @create 2022-08-12
 */
// T为Buffer内的数据类型
public class BufferPool<T extends StreamElement> {
    private List<T> list;

    private AtomicInteger offset;
    //一个Buffer的数据可以分发给下游多个InputChannel，可以根据数据的哈希值选择发往哪一个InputChannel
    private List<InputChannel<T>> channels;

    private boolean isPartition;

    public List<T> getList() {
        return list;
    }

    public BufferPool(){
        list = new CopyOnWriteArrayList<>();
        offset = new AtomicInteger(list.size());
        channels = new ArrayList<>();
        isPartition = false;
    }

    public void enablePartition(){
        isPartition = true;
    }
    public void add(T data){
        list.add(data);
    }

    public T take(int index){
        //防止数组越界
        if(index >= list.size()) return null;
        return list.get(index);
    }

    private Random random = new Random();
    //将数据推向下游
    public void push(T data){
        push(data,null);
    }

    //强行默认以String类型为key
    //TODO 只为了能实现相同单词到同一个管道，后面可能要改
    public void push(T data, KeySelector<T,String> keySelector){
        //先加入缓冲池
        add(data);
        int channelIndex;
        if(keySelector == null){
            //没有分区需求，随机放置
            channelIndex = random.nextInt(channels.size());
        }else {
            if(data.isRecord()){
                StreamRecord<T> record = data.asRecord();
                //如果是StreamRecord类型且有分区需求，根据哈希值放入对应
                //根据keySelector获取key，根据key的哈希值放入对应管道
                String key = keySelector.getKey(record.getValue());
                int hash = key.hashCode();
                channelIndex = hash % channels.size();
            }else {
                //其他情况
                channelIndex = 0;
            }
        }
        channels.get(channelIndex).add(data);
    }

    //为当前数据源绑定一个下游输出
    public void bindInputChannel(List<InputChannel<T>> channels){
        this.channels = channels;
    }
}
