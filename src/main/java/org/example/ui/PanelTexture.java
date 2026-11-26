package org.example.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;

import org.lwjgl.system.MemoryUtil;

final class PanelTexture implements AutoCloseable {
    private final BufferedImage image;
    private final ByteBuffer rgbaPixels;

    PanelTexture(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        rgbaPixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4))
                .order(ByteOrder.BIG_ENDIAN);
    }

    ByteBuffer paint(PanelUiLayout layout, Consumer<Graphics2D> painter) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, layout.pixelWidth, layout.pixelHeight);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.scale(layout.pixelWidth / (double) layout.logicalWidth, 1.0);
            Shape originalClip = graphics.getClip();
            graphics.clip(layout.roundedPanelClip());

            painter.accept(graphics);
            graphics.setClip(originalClip);
        } finally {
            graphics.dispose();
        }
        rgbaPixels.clear();
        int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int pixel : argb) rgbaPixels.putInt(Integer.rotateLeft(pixel, 8));
        return rgbaPixels.flip();
    }

    @Override
    public void close() {
        MemoryUtil.memFree(rgbaPixels);
    }
}
