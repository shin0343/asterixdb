/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.runtime.unnestingfunctions.std;

import org.apache.asterix.dataflow.data.nontagged.serde.AInt64SerializerDeserializer;
import org.apache.asterix.formats.nontagged.SerializerDeserializerProvider;
import org.apache.asterix.om.base.AInt64;
import org.apache.asterix.om.types.BuiltinType;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.algebricks.runtime.base.IUnnestingEvaluator;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.junit.Assert;
import org.junit.Test;

public class RangeDescriptorTest {

    @Test
    public void maximumSingletonTerminates() throws Exception {
        assertRange(evaluator(new long[] { Long.MAX_VALUE, Long.MAX_VALUE }), Long.MAX_VALUE);
    }

    @Test
    public void rangeEndingAtMaximumTerminates() throws Exception {
        assertRange(evaluator(new long[] { Long.MAX_VALUE - 1, Long.MAX_VALUE }), Long.MAX_VALUE - 1, Long.MAX_VALUE);
    }

    @Test
    public void ordinaryRangeIncludesBothBounds() throws Exception {
        assertRange(evaluator(new long[] { -1, 1 }), -1, 0, 1);
    }

    @Test
    public void minimumRangeIncludesBothBounds() throws Exception {
        assertRange(evaluator(new long[] { Long.MIN_VALUE, Long.MIN_VALUE + 1 }), Long.MIN_VALUE, Long.MIN_VALUE + 1);
    }

    @Test
    public void reversedRangeIsEmpty() throws Exception {
        assertRange(evaluator(new long[] { Long.MAX_VALUE, Long.MIN_VALUE }));
    }

    @Test
    public void evaluatorCanBeReinitializedAfterMaximum() throws Exception {
        long[] bounds = { Long.MAX_VALUE, Long.MAX_VALUE };
        IUnnestingEvaluator evaluator = evaluator(bounds);
        assertRange(evaluator, Long.MAX_VALUE);
        bounds[0] = 1;
        bounds[1] = 2;
        assertRange(evaluator, 1, 2);
        bounds[0] = Long.MAX_VALUE;
        bounds[1] = Long.MAX_VALUE;
        assertRange(evaluator, Long.MAX_VALUE);
    }

    @Test
    public void evaluatorCanBeReinitializedAfterEmptyRange() throws Exception {
        long[] bounds = { 1, 0 };
        IUnnestingEvaluator evaluator = evaluator(bounds);
        assertRange(evaluator);
        bounds[0] = Long.MIN_VALUE;
        bounds[1] = Long.MIN_VALUE;
        assertRange(evaluator, Long.MIN_VALUE);
    }

    private static IUnnestingEvaluator evaluator(long[] bounds) throws Exception {
        return new RangeDescriptor()
                .createUnnestingEvaluatorFactory(
                        new IScalarEvaluatorFactory[] { argument(bounds, 0), argument(bounds, 1) })
                .createUnnestingEvaluator(null);
    }

    private static IScalarEvaluatorFactory argument(long[] bounds, int index) {
        return ctx -> (tuple, result) -> {
            ArrayBackedValueStorage storage = new ArrayBackedValueStorage();
            SerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.AINT64)
                    .serialize(new AInt64(bounds[index]), storage.getDataOutput());
            result.set(storage);
        };
    }

    private static void assertRange(IUnnestingEvaluator evaluator, long... expected) throws Exception {
        evaluator.init(null);
        VoidPointable result = new VoidPointable();
        for (long value : expected) {
            Assert.assertTrue(evaluator.step(result));
            Assert.assertEquals(value,
                    AInt64SerializerDeserializer.getLong(result.getByteArray(), result.getStartOffset() + 1));
        }
        Assert.assertFalse("range must terminate after its inclusive upper bound", evaluator.step(result));
        Assert.assertFalse("an exhausted range must remain exhausted", evaluator.step(result));
    }
}
