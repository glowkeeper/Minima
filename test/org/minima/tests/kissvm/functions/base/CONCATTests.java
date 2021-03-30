package org.minima.tests.kissvm.functions.base;

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
import org.minima.kissvm.functions.base.CONCAT;
import org.minima.kissvm.values.BooleanValue;
import org.minima.kissvm.values.HEXValue;
import org.minima.kissvm.values.NumberValue;
import org.minima.kissvm.values.StringValue;
import org.minima.kissvm.values.Value;
import org.minima.objects.Transaction;
import org.minima.objects.Witness;

//HEXValue CONCAT (HEXValue arg1 … HEXValue argN)
//ScriptValue CONCAT (ScriptValue arg1 … ScriptValue argN)
public class CONCATTests {

    @Test
    public void testConstructors() {
        CONCAT fn = new CONCAT();
        MinimaFunction mf = fn.getNewFunction();

        assertEquals("CONCAT", mf.getName());
        assertEquals(0, mf.getParameterNum());

        try {
            mf = MinimaFunction.getFunction("CONCAT");
            assertEquals("CONCAT", mf.getName());
            assertEquals(0, mf.getParameterNum());
        } catch (MinimaParseException ex) {
            fail();
        }
    }

    @Test
    public void testValidParams() {
        Contract ctr = new Contract("", "", new Witness(), new Transaction(), new ArrayList<>());

        CONCAT fn = new CONCAT();

        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            mf.addParameter(new ConstantExpression(new HEXValue("0x89ABCDEF")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_HEX, res.getValueType());
                assertEquals("0x0123456789ABCDEF", ((HEXValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            mf.addParameter(new ConstantExpression(new HEXValue("0x89ABCDEF")));
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            mf.addParameter(new ConstantExpression(new HEXValue("0x89ABCDEF")));
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_HEX, res.getValueType());
                assertEquals("0x0123456789ABCDEF0123456789ABCDEF01234567", ((HEXValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("")));
            mf.addParameter(new ConstantExpression(new StringValue("")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("HELLO")));
            mf.addParameter(new ConstantExpression(new StringValue("WORLD")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("hello world", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("HELLO")));
            mf.addParameter(new ConstantExpression(new StringValue("WORLD")));
            mf.addParameter(new ConstantExpression(new StringValue("MINIMA")));
            mf.addParameter(new ConstantExpression(new StringValue("IS")));
            mf.addParameter(new ConstantExpression(new StringValue("HERE")));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_STRING, res.getValueType());
                assertEquals("hello world MINIMA is here", ((StringValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
    }

    @Test
    public void testInvalidParams() {
        Contract ctr = new Contract("", "", new Witness(), new Transaction(), new ArrayList<>());

        CONCAT fn = new CONCAT();

        // Invalid param count
        {
            MinimaFunction mf = fn.getNewFunction();
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("HELLO")));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }

        // Invalid param domain
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue(""))); // should fail for invalid input
            mf.addParameter(new ConstantExpression(new HEXValue("")));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_HEX, res.getValueType());
                assertEquals("0x", ((HEXValue) res).toString()); // invalid HEX number representation
            } catch (ExecutionException ex) {
                fail();
            }
        }

        // Invalid param types
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            mf.addParameter(new ConstantExpression(new BooleanValue(true)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            mf.addParameter(new ConstantExpression(new NumberValue(100)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            mf.addParameter(new ConstantExpression(new StringValue("HELLO")));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("HELLO")));
            mf.addParameter(new ConstantExpression(new BooleanValue(true)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("HELLO")));
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new StringValue("HELLO")));
            mf.addParameter(new ConstantExpression(new NumberValue(100)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new BooleanValue(true)));
            mf.addParameter(new ConstantExpression(new BooleanValue(true)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new NumberValue(100)));
            mf.addParameter(new ConstantExpression(new NumberValue(100)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }

    }
}
