package com.ptmuk.block;

/** Visual style of a UK signal head. Behaviour is identical; only the models differ. */
public enum SignalStyle {
    /** Modern LED heads (Siemens Helios / Dynniq style) with a yellow-bordered backing board. */
    MODERN,
    /** Older incandescent heads (GEC / Peek style) with long hoods and a white-bordered backing board. */
    CLASSIC
}
