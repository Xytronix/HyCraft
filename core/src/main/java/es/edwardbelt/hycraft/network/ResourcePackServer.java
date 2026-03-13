package es.edwardbelt.hycraft.network;

import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import es.edwardbelt.hycraft.util.Logger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.MessageDigest;

public class ResourcePackServer {
    private static ResourcePackServer INSTANCE;
    public static ResourcePackServer get() { return INSTANCE; }

    private static final String PACK_PATH = "mods/HyCraft/resourcepack.zip";

    private Channel listener;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private byte[] packData;
    private String packHash;

    public void start(int port) {
        INSTANCE = this;

        File packFile = new File(PACK_PATH);
        if (!packFile.exists()) {
            Logger.INFO.log("No resource pack found at " + PACK_PATH + ", HTTP server not started");
            return;
        }

        try {
            packData = Files.readAllBytes(packFile.toPath());
            packHash = computeSha1(packData);
            Logger.INFO.log("Resource pack loaded: " + packData.length + " bytes, SHA1: " + packHash);
        } catch (Exception e) {
            Logger.ERROR.log("Failed to load resource pack: " + e.getMessage());
            return;
        }

        try {
            bossGroup = NettyUtil.getEventLoopGroup(1, "RP-BossGroup");
            workerGroup = NettyUtil.getEventLoopGroup("RP-WorkerGroup");

            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NettyUtil.getServerChannel())
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(65536))
                                    .addLast(new PackHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            if (future.isSuccess()) {
                listener = future.channel();
                Logger.INFO.log("Resource pack HTTP server started on port " + port);
            }
        } catch (Exception e) {
            Logger.ERROR.log("Failed to start resource pack HTTP server: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (listener != null) {
            try { listener.close().sync(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

    public boolean isAvailable() {
        return packData != null;
    }

    public String getPackHash() {
        return packHash;
    }

    public String buildUrl(String serverAddress, int httpPort) {
        String host = serverAddress;
        // Strip Forge/FML markers (e.g., "localhost\0FML3\0")
        int nullIdx = host.indexOf('\0');
        if (nullIdx > 0) host = host.substring(0, nullIdx);
        return "http://" + host + ":" + httpPort + "/hycraft.zip";
    }

    private static String computeSha1(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private class PackHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (!"/hycraft.zip".equals(request.uri())) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(packData)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/zip");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, packData.length);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
