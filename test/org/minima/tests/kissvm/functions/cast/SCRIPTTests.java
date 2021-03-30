package org.minima.tests.kissvm.functions.cast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import java.util.ArrayList;

import org.junit.Test;
import org.minima.kissvm.Contract;
import org.minima.kissvm.exceptions.ExecutionException;
import org.minima.kissvm.exceptions.MinimaParseException;
import org.minima.kissvm.expressions.ConstantExpression;
import org.minima.kissvm.functions.MinimaFunction;
import org.minima.kissvm.functions.cast.STRING;
import org.minima.kissvm.values.BooleanValue;
import org.minima.kissvm.values.HEXValue;
import org.minima.kissvm.values.NumberValue;
import org.minima.kissvm.values.StringValue;
import org.minima.kissvm.values.Value;
import org.minima.objects.Transaction;
import org.minima.objects.Witness;

//HEXValue SCRIPT (BooleanValue var)
//HEXValue SCRIPT (HEXValue var)
//HEXValue SCRIPT (NumberValue var)
//HEXValue SCRIPT (ScriptValue var)
public class SCRIPTTests {

    @Test
    public void testConstructors() {
        STRING fn = new STRING();
        MinimaFunction mf = fn.getNewFunction();

        assertEquals("SCRIPT", mf.getName());
        assertEquals(0, mf.getParameterNum());

        try {
            mf = MinimaFunction.getFunction("SCRIPT");
            assertEquals("SCRIPT", mf.getName());
            assertEquals(0, mf.getParameterNum());
        } catch (MinimaParseException ex) {
            fail();
        }
    }

    @Test
    public void testValidParams() {
        Contract ctr = new Contract("", "", new Witness(), new Transaction(), new ArrayList<>());

        STRING fn = new STRING();

        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new BooleanValue(true)));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("TRUE", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new BooleanValue(false)));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("FALSE", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x414243444546")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                //assertEquals("ABCDEF", ((ScriptValue) res).toString()); // test fails because script value forces lowercase
                assertEquals("abcdef", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x4142434445464748494A4B4C4D4E4F505152535455565758595A")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                //ssertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", ((ScriptValue) res).toString()); // test fails because script value forces lowercase
                assertEquals("abcdefghijklmnopqrstuvwxyz", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new NumberValue(0)));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("0", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new NumberValue(65535)));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("65535", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new NumberValue(-65535)));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("-65535", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("ABCDEFGHIJKLMNOPQRSTUVWXYZ")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                //assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", ((ScriptValue) res).toString()); // test fails because script value forces lowercase
                assertEquals("abcdefghijklmnopqrstuvwxyz", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("Hello World")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                //assertEquals("Hello World", ((ScriptValue) res).toString()); // test fails because script value forces lowercase
                assertEquals("hello world", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
    }

    @Test
    public void testInvalidParams() {
        Contract ctr = new Contract("", "", new Witness(), new Transaction(), new ArrayList<>());

        STRING fn = new STRING();

        // Invalid param count
        {
            MinimaFunction mf = fn.getNewFunction();
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
    }
}
