package me.xiaozhangup.octopus.hamster;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

final class HamsterAppender extends AbstractAppender {
    static final String NAME = "Hamster";
    private final HamsterRecorder recorder;

    HamsterAppender(HamsterRecorder recorder) {
        super(NAME, null, null, true, Property.EMPTY_ARRAY);
        this.recorder = recorder;
    }

    @Override
    public void append(LogEvent event) {
        this.recorder.accept(event);
    }
}
