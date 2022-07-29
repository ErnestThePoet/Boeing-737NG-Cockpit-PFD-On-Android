package com.ecui.efis;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class SWFilterTest {
    @Test
    public void test() {
        SWFilter filter=new SWFilter(3);

        float f1=1.0f;
        float f2=2.0f;
        float f3=3.0f;
        float f4=-1.0f;
        float f5=-2.0f;

        assertEquals(1.0f,filter.filter(f1),0.001f);
        assertEquals(1.5f,filter.filter(f2),0.001f);
        assertEquals(2.0f,filter.filter(f3),0.001f);
        assertEquals((4.0f/3),filter.filter(f4),0.001f);
        assertEquals(0.0f,filter.filter(f5),0.001f);

    }
}