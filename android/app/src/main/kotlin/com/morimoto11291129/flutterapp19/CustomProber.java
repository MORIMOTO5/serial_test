package com.morimoto11291129.flutterapp19;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class CustomProber {

    static UsbSerialProber getCustomProber() {
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x16d0, 0x087e, CdcAcmSerialDriver.class); // e.g. Digispark CDC
        customTable.addProduct(0x0DF5, 0x1015, FtdiSerialDriver.class);
        customTable.addProduct(0x0403, 0x6010, CdcAcmSerialDriver.class);
        return new UsbSerialProber(customTable);
    }

}
