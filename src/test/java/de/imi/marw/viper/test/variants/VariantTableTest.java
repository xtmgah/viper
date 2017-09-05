/* Copyright (c) 2017 Marius Wöste
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package de.imi.marw.viper.test.variants;

import de.imi.marw.viper.variants.VariantPropertyType;
import de.imi.marw.viper.variants.filters.StringFilter;
import de.imi.marw.viper.variants.table.VariantTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author marius
 */
public class VariantTableTest {

    @Test(expected = IllegalArgumentException.class)
    public void duplicateColumnsThrowException() {

        List<List<Object>> calls = new ArrayList<>();
        List<VariantPropertyType> types = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS_TYPES));
        types.add(VariantPropertyType.NUMERIC_COLLECTION);

        List<String> colNames = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS));
        colNames.add(VariantTable.BP1_COLUMN_NAME);

        VariantTable duplicate = new VariantTable(calls, colNames, types);

    }

    @Test
    public void emptyTableWorksCorrectly() {

        List<List<Object>> calls = new ArrayList<>();
        List<VariantPropertyType> types = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS_TYPES));
        List<String> colNames = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS));

        VariantTable emptyTable = new VariantTable(calls, colNames, types);

        assertEquals(emptyTable.getNumberOfCalls(), 0);
        assertEquals(emptyTable.getRawCalls().size(), 0);
        assertEquals(emptyTable.getUnfilteredColumn(VariantTable.BP1_COLUMN_NAME).size(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingMandatoryFieldsThrowsException() {

        List<List<Object>> calls = new ArrayList<>();
        List<VariantPropertyType> types = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS_TYPES));
        List<String> colNames = new ArrayList<>();
        colNames.add(VariantTable.SAMPLE_COLUMN_NAME);
        colNames.add(VariantTable.BP1_COLUMN_NAME);

        VariantTable missingColumns = new VariantTable(calls, colNames, types);

    }

    private void testWrongColumnType(int i, Object wrongValue) {
        List<List<Object>> calls = new ArrayList<>();
        List<VariantPropertyType> types = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS_TYPES));
        types.add(VariantPropertyType.STRING_COLLECTION);
        types.add(VariantPropertyType.NUMERIC_COLLECTION);

        List<String> colNames = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS));
        colNames.add("strColl");
        colNames.add("numColl");

        List<Object> call = new ArrayList<>();
        call.add("SAMPLE1");
        call.add("DELETION");
        call.add("17");
        call.add(12341234.0);
        call.add("18");
        call.add(12324565.0);
        call.add(Arrays.asList(new String[]{"blub", "Halhalo"}));
        call.add(Arrays.asList(new Double[]{13.0, 37.0}));

        call.set(i, wrongValue);
        calls.add(call);

        try {
            VariantTable noDataIntegrity = new VariantTable(calls, colNames, types);
            fail("data integrity was violated when creating variant table with false data types!");
        } catch (IllegalArgumentException ex) {
            // this is supposed to happen!
        }

    }

    @Test
    public void wrongColumnTypeYieldsError() {

        Collection<String> stringCollectionValue = new ArrayList<>();
        stringCollectionValue.add("blub");
        stringCollectionValue.add("blar");

        Collection<Double> numericCollectionValue = new ArrayList<>();
        numericCollectionValue.add(13.0);
        numericCollectionValue.add(37.0);

        int stringColumnIndex = Arrays.asList(VariantTable.MANDATORY_FIELDS).indexOf(VariantTable.SAMPLE_COLUMN_NAME);

        testWrongColumnType(stringColumnIndex, 0);
        testWrongColumnType(stringColumnIndex, 0.0);
        testWrongColumnType(stringColumnIndex, '1');
        testWrongColumnType(stringColumnIndex, stringCollectionValue);
        testWrongColumnType(stringColumnIndex, numericCollectionValue);

        int numericColumnIndex = Arrays.asList(VariantTable.MANDATORY_FIELDS).indexOf(VariantTable.BP1_COLUMN_NAME);

        testWrongColumnType(numericColumnIndex, 0);
        testWrongColumnType(numericColumnIndex, "0.0");
        testWrongColumnType(numericColumnIndex, '1');
        testWrongColumnType(numericColumnIndex, stringCollectionValue);
        testWrongColumnType(numericColumnIndex, numericCollectionValue);

        int stringCollectionColumnIndex = VariantTable.MANDATORY_FIELDS.length;

        testWrongColumnType(stringCollectionColumnIndex, 0);
        testWrongColumnType(stringCollectionColumnIndex, 0.0);
        testWrongColumnType(stringCollectionColumnIndex, "0.0");
        testWrongColumnType(stringCollectionColumnIndex, '1');
        testWrongColumnType(stringCollectionColumnIndex, numericCollectionValue);

        int numericCollectionColumnIndex = VariantTable.MANDATORY_FIELDS.length + 1;

        testWrongColumnType(numericCollectionColumnIndex, 0);
        testWrongColumnType(numericCollectionColumnIndex, 0.0);
        testWrongColumnType(numericCollectionColumnIndex, "0.0");
        testWrongColumnType(numericCollectionColumnIndex, '1');
        testWrongColumnType(numericCollectionColumnIndex, stringCollectionValue);

    }

    private VariantTable createSimpleTable() {
        List<List<Object>> calls = new ArrayList<>();
        List<VariantPropertyType> types = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS_TYPES));
        types.add(VariantPropertyType.STRING_COLLECTION);
        types.add(VariantPropertyType.NUMERIC_COLLECTION);

        List<String> colNames = new ArrayList<>(Arrays.asList(VariantTable.MANDATORY_FIELDS));
        colNames.add("strColl");
        colNames.add("numColl");

        List<Object> call = new ArrayList<>();
        call.add("SAMPLE1");
        call.add("DELETION");
        call.add("17");
        call.add(12341234.0);
        call.add("19");
        call.add(12524565.0);
        call.add(Arrays.asList(new String[]{"blub", "Halhalo"}));
        call.add(Arrays.asList(new Double[]{13.0, 37.0}));

        List<Object> call2 = new ArrayList<>();
        call2.add("SAMPLE2");
        call2.add("DELETION");
        call2.add("17");
        call2.add(12341234.1234);
        call2.add("18");
        call2.add(12524565.5321);
        call2.add(Arrays.asList(new String[]{}));
        call2.add(Arrays.asList(new Double[]{}));

        calls.add(call);
        calls.add(call2);

        VariantTable simpleTable = new VariantTable(calls, colNames, types);

        return simpleTable;
    }

    @Test
    public void correctTableIsCreated() {

        VariantTable simpleTable = createSimpleTable();

        assertEquals(simpleTable.getNumberOfCalls(), 2);
        assertEquals(simpleTable.getUnfilteredColumn(VariantTable.BP1_COLUMN_NAME), Arrays.asList(new Double[]{12341234.0, 12341234.1234}));
        assertEquals(simpleTable.getUnfilteredColumn(VariantTable.SAMPLE_COLUMN_NAME), Arrays.asList(new String[]{"SAMPLE1", "SAMPLE2"}));
        assertEquals(simpleTable.getCall(0).get("strColl"), Arrays.asList(new String[]{"blub", "Halhalo"}));
        assertEquals(simpleTable.getCall(1).get(VariantTable.CHR2_COLUMN_NAME), "18");

        simpleTable.setCallProperty(0, VariantTable.BP1_COLUMN_NAME, 37.0);
        assertEquals(simpleTable.getCall(0).get(VariantTable.BP1_COLUMN_NAME), 37.0);
    }

    @Test
    public void softFiltersAreWorkingCorrectly() {

        Set<String> allowedStringValues = new HashSet<>();
        allowedStringValues.add("SAMPLE2");

        StringFilter filter = new StringFilter(VariantTable.SAMPLE_COLUMN_NAME);
        filter.setAllowedValues(allowedStringValues);
        VariantTable filteredTable = createSimpleTable();
        filteredTable.filter(Arrays.asList(filter));

        assertEquals(filteredTable.getNumberOfCalls(), 1);
        assertEquals(filteredTable.getUnfilteredColumn(VariantTable.BP1_COLUMN_NAME), Arrays.asList(new Double[]{12341234.0, 12341234.1234}));
        assertEquals(filteredTable.getUnfilteredColumn(VariantTable.SAMPLE_COLUMN_NAME), Arrays.asList(new String[]{"SAMPLE1", "SAMPLE2"}));
        assertEquals(filteredTable.getCall(0).get("strColl"), Arrays.asList(new String[]{}));
        assertEquals(filteredTable.getCall(0).get(VariantTable.CHR2_COLUMN_NAME), "18");

        filteredTable.setCallProperty(0, VariantTable.BP1_COLUMN_NAME, 37.0);
        assertEquals(filteredTable.getCall(0).get(VariantTable.BP1_COLUMN_NAME), 37.0);

        allowedStringValues.clear();
        filteredTable.filter(Arrays.asList(filter));

        assertEquals(filteredTable.getNumberOfCalls(), 2);
        assertEquals(filteredTable.getUnfilteredColumn(VariantTable.BP1_COLUMN_NAME), Arrays.asList(new Double[]{12341234.0, 37.0}));
        assertEquals(filteredTable.getUnfilteredColumn(VariantTable.SAMPLE_COLUMN_NAME), Arrays.asList(new String[]{"SAMPLE1", "SAMPLE2"}));
        assertEquals(filteredTable.getCallProperty(0, "strColl"), Arrays.asList(new String[]{"blub", "Halhalo"}));
        assertEquals(filteredTable.getCallProperty(1, VariantTable.CHR2_COLUMN_NAME), "18");
    }
}
