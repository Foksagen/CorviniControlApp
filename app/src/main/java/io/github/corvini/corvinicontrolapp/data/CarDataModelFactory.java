package io.github.corvini.corvinicontrolapp.data;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import io.github.corvini.corvinicontrolapp.References;

public class CarDataModelFactory implements ViewModelProvider.Factory {

    private final byte[] initPacketData;

    public CarDataModelFactory(byte[] initPacketData) {

        if (initPacketData.length != References.MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Invalid data with length " + initPacketData.length);
        }

        this.initPacketData = initPacketData;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new CarDataModel(this.initPacketData);
    }
}
