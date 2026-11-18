package com.example.picturebackend;

import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.manager.RemoteImageFetcher;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;

class RemoteImageFetcherTests {
    @Test void refusesPrivateAndReservedAddressRanges() throws Exception {
        assertFalse(RemoteImageFetcher.isPublicAddress(InetAddress.getLoopbackAddress(),new byte[]{127,0,0,1}));
        assertFalse(RemoteImageFetcher.isPublicAddress(InetAddress.getByAddress(new byte[]{(byte)169,(byte)254,1,2}),new byte[]{(byte)169,(byte)254,1,2}));
        assertFalse(RemoteImageFetcher.isPublicAddress(InetAddress.getByAddress(new byte[]{(byte)192,0,2,1}),new byte[]{(byte)192,0,2,1}));
        assertTrue(RemoteImageFetcher.isPublicAddress(InetAddress.getByAddress(new byte[]{1,1,1,1}),new byte[]{1,1,1,1}));
    }
    @Test void rejectsLoopbackBeforeConnecting(){
        assertThrows(BusinessException.class,()->new RemoteImageFetcher().fetch("http://127.0.0.1/secret",1024));
    }
}
