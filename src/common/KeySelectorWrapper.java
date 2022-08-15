package common;

import function.KeySelector;
import record.StreamElement;
import record.StreamRecord;

/**
 * @author kevin.zeng
 * @description 将KeySelector<T,String>转换为KeySelector<StreamElement,String>
 * @create 2022-08-13
 */
public class KeySelectorWrapper<T> {

    public KeySelector<StreamElement,String> convert(KeySelector<T,String> keySelector) {
        return value ->{
            StreamRecord<T> record = value.asRecord();
            return keySelector.getKey(record.getValue());
        };
    }
}