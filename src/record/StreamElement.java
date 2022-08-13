package record;

/**
 * @author kevin.zeng
 * @description
 * @create 2022-08-13
 */
public class StreamElement {
    public final boolean isRecord() {
        return getClass() == StreamRecord.class;
    }

    public final <T> StreamRecord<T> asRecord() {
        return (StreamRecord<T>) this;
    }
}