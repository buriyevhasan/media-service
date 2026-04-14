package com.mediaservice.dto;

// SignalMessage.java
// Serverdan browserga qaytadi: "mana javob"
public record SignalMessage(
        String type,       // "ready" | "answer" | "iceCandidate" | "peer-left" | "full"
        String sdp,        // answer SDP (faqat "answer" typeda to'ladi, qolganda null)
        String candidate,  // ICE candidate (faqat "iceCandidate" typeda to'ladi)
        String sdpMid,     // ICE uchun (faqat "iceCandidate" typeda to'ladi)
        int sdpMLineIndex, // ICE uchun (faqat "iceCandidate" typeda to'ladi)
        String username    // kim haqida xabar (peer-left da kim ketdi)
) {}