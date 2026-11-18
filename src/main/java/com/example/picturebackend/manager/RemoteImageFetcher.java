package com.example.picturebackend.manager;

import com.example.picturebackend.exception.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;

/** DNS is validated by the connection resolver, including reconnects (no DNS rebinding gap). */
@Component
public class RemoteImageFetcher {
    public byte[] fetch(String address,int max){
        try {
            URI uri=new URI(address);
            ThrowUtils.throwIf(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || (uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443),ErrorCode.PARAMS_ERROR,"仅支持公网 HTTP/HTTPS 链接");
            try(CloseableHttpClient client=HttpClients.custom().disableRedirectHandling().disableAutomaticRetries()
                .setDnsResolver(host->{InetAddress[] addresses=SystemDefaultDnsResolver.INSTANCE.resolve(host);
                    for(InetAddress ip:addresses){byte[] b=ip.getAddress();
                        if(!isPublicAddress(ip,b))
                            throw new UnknownHostException("禁止访问非公网地址");
                    }return addresses;})
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(5000).setSocketTimeout(10000).setConnectionRequestTimeout(5000).build()).build()){
                HttpGet get=new HttpGet(uri);get.setHeader("User-Agent","CloudGallery/1.0");
                try(CloseableHttpResponse response=client.execute(get)){
                    ThrowUtils.throwIf(response.getStatusLine().getStatusCode()!=200 || response.getEntity()==null,ErrorCode.PARAMS_ERROR,"下载失败，暂不支持重定向链接");
                    ThrowUtils.throwIf(response.getEntity().getContentLength()>max,ErrorCode.PARAMS_ERROR,"远程文件过大");
                    try(InputStream in=response.getEntity().getContent();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){
                            ThrowUtils.throwIf(out.size()+n>max,ErrorCode.PARAMS_ERROR,"远程文件过大");out.write(buf,0,n);
                        }return out.toByteArray();
                    }
                }
            }
        }catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(ErrorCode.PARAMS_ERROR,"无法下载该公网链接");}
    }
    public static boolean isPublicAddress(InetAddress ip,byte[] b){
        if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())return false;
        if(b.length==16){
            // Permit global unicast only; reject ULA, link-local, mapped IPv4 and transition/documentation ranges.
            return (b[0]&0xe0)==0x20 && !(b[0]==0x20&&b[1]==0x01&&((b[2]&0xf8)==0x00||(b[2]&255)==0x0d))
                    && !(b[0]==0x20&&b[1]==0x02);
        }
        int a=b[0]&255,c=b[1]&255,d=b[2]&255;
        return a!=0 && a!=10 && a!=127 && a<224
                && !(a==100&&c>=64&&c<=127)
                && !(a==169&&c==254)
                && !(a==172&&c>=16&&c<=31)
                && !(a==192&&(c==0||c==2||c==88||c==168))
                && !(a==198&&(c==18||c==19||c==51))
                && !(a==203&&c==0&&d==113);
    }
}
