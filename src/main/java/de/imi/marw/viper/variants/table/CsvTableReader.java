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
package de.imi.marw.viper.variants.table;

import au.com.bytecode.opencsv.CSVReader;
import de.imi.marw.viper.util.Util;
import de.imi.marw.viper.variants.VariantPropertyType;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author marius
 */
public class CsvTableReader implements TableReader {

    private final char csvDelimiter;
    private final String propertyDelimiter;

    public CsvTableReader(char csvDelimiter, String propertyDelimiter) {
        this.csvDelimiter = csvDelimiter;
        this.propertyDelimiter = propertyDelimiter;
    }

    private VariantPropertyType determineType(List<String> columnValues) {

        String doubleRegex = "(NA|(" + Util.FP_REGEX + "))";

        boolean isNumericColumn = columnValues.stream()
                .allMatch((str) -> str.matches(doubleRegex));

        if (isNumericColumn) {
            return VariantPropertyType.NUMERIC;
        }

        String numericCollectionRegex = doubleRegex + "(" + this.propertyDelimiter + "\\s*" + doubleRegex + ")*";

        boolean isNumericCollectionColumn = columnValues.stream()
                .allMatch((str) -> str.matches(numericCollectionRegex));

        if (isNumericCollectionColumn) {
            return VariantPropertyType.NUMERIC_COLLECTION;
        }

        boolean isStringCollection = columnValues.stream()
                .anyMatch((str) -> str.contains(this.propertyDelimiter));

        if (isStringCollection) {
            return VariantPropertyType.STRING_COLLECTION;
        }

        return VariantPropertyType.STRING;
    }

    private VariantPropertyType[] determineTypes(List<String[]> stringTable) {

        VariantPropertyType[] types = IntStream.range(0, stringTable.get(0).length).boxed()
                .map((i) -> {

                    List<String> singlePropertyValues = stringTable.stream()
                            .map((rowValues) -> rowValues[i])
                            .collect(Collectors.toList());

                    return singlePropertyValues;
                })
                .map(this::determineType)
                .toArray(VariantPropertyType[]::new);

        return types;
    }

    private Double parseDouble(String str) {
        if (str.equals("NA")) {
            return null;
        } else {
            return Double.parseDouble(str);
        }
    }

    private Object parseProperty(VariantPropertyType type, String rawValue) {
        switch (type) {
            case NUMERIC:
                return parseDouble(rawValue);
            case STRING:
                return rawValue;
            case NUMERIC_COLLECTION:

                Collection<Double> numbers = Arrays.stream(rawValue.split(this.propertyDelimiter))
                        .map(this::parseDouble)
                        .collect(Collectors.toList());

                return (numbers);
            case STRING_COLLECTION:

                Collection<String> strings = Arrays.asList(rawValue.split(this.propertyDelimiter));
                return strings;

            default:
                throw new IllegalStateException("Unexpected type " + type + " when parsing " + rawValue);
        }
    }

    private List<Object> parseVariantCall(String[] colNames, String[] colValues, VariantPropertyType[] types) {
        Object[] row = new Object[colNames.length];

        for (int i = 0; i < colNames.length; i++) {
            Object propertyValue = parseProperty(types[i], colValues[i]);

            row[i] = propertyValue;
        }

        return Arrays.asList(row);
    }

    private List<List<Object>> parseVariantCalls(String[] colNames, List<String[]> colValues, VariantPropertyType[] types) {

        List<List<Object>> calls = colValues.stream()
                .map((rowValues) -> parseVariantCall(colNames, rowValues, types))
                .collect(Collectors.toList());

        return calls;
    }

    @Override
    public VariantTable readTable(String fileName) {
        try (CSVReader reader = new CSVReader(new FileReader(fileName), this.csvDelimiter)) {

            String[] header = reader.readNext();

            List<String[]> rawData = reader.readAll();

            VariantPropertyType[] types = determineTypes(rawData);

            List<List<Object>> parsedCalls = parseVariantCalls(header, rawData, types);

            return new VariantTable(parsedCalls, Arrays.asList(header), Arrays.asList(types));

        } catch (FileNotFoundException ex) {
            System.out.println("[ERROR] File " + fileName + " not found!");
        } catch (IOException ex) {
            System.out.println("[ERROR] IOException when reading csv file:");
            System.err.print(ex);
        }

        return null;
    }

}
