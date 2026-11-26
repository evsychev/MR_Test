package org.example.xr;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.FBPassthrough;
import org.lwjgl.openxr.XrPassthroughCreateInfoFB;
import org.lwjgl.openxr.XrPassthroughFB;
import org.lwjgl.openxr.XrPassthroughLayerCreateInfoFB;
import org.lwjgl.openxr.XrPassthroughLayerFB;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.xr.OpenXrSupport.check;

final class FbPassthrough {
    private static final Logger LOGGER = LoggerFactory.getLogger(FbPassthrough.class);
    private FbPassthrough() {}

    static XrPassthroughLayerFB start(XrSession session, MemoryStack stack, SessionResources resources) {
        PointerBuffer passthroughHandle = stack.mallocPointer(1);
        var passthroughInfo = XrPassthroughCreateInfoFB.calloc(stack)
                .type$Default().flags(FBPassthrough.XR_PASSTHROUGH_IS_RUNNING_AT_CREATION_BIT_FB);
        check(FBPassthrough.xrCreatePassthroughFB(session, passthroughInfo, passthroughHandle),
                "create passthrough feature");
        var passthrough = new XrPassthroughFB(passthroughHandle.get(0), session);
        resources.defer(() -> check(FBPassthrough.xrDestroyPassthroughFB(passthrough), "destroy passthrough resource"));
        check(FBPassthrough.xrPassthroughStartFB(passthrough), "start passthrough");
        PointerBuffer passthroughLayerHandle = stack.mallocPointer(1);
        var passthroughLayerInfo = XrPassthroughLayerCreateInfoFB.calloc(stack)
                .type$Default().passthrough(passthrough)
                .purpose(FBPassthrough.XR_PASSTHROUGH_LAYER_PURPOSE_RECONSTRUCTION_FB);
        check(FBPassthrough.xrCreatePassthroughLayerFB(
                session, passthroughLayerInfo, passthroughLayerHandle), "create passthrough layer");
        var passthroughLayer = new XrPassthroughLayerFB(passthroughLayerHandle.get(0), session);
        resources.defer(() -> check(FBPassthrough.xrDestroyPassthroughLayerFB(passthroughLayer), "destroy passthrough resource"));
        check(FBPassthrough.xrPassthroughLayerResumeFB(passthroughLayer), "resume passthrough layer");
        LOGGER.info("XR passthrough feature and reconstruction layer are active");
        return passthroughLayer;
    }
}
