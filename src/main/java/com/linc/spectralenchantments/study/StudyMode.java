package com.linc.spectralenchantments.study;

import net.minecraft.util.StringRepresentable;

/** The two things a lectern can do with enchantments. */
public enum StudyMode implements StringRepresentable {
    /** An enchanted item, rubbed with amethyst, is destroyed to write one of its enchantments into the lectern's book and quill. */
    STUDYING("studying"),
    /** One of the enchantments of a held enchanted book is written into the lectern's book and quill. */
    COPYING("copying");

    private final String name;

    StudyMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
