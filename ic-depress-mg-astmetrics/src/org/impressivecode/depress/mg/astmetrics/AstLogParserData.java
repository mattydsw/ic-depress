/*
ImpressiveCode Depress Framework
Copyright (C) 2013  ImpressiveCode contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.impressivecode.depress.mg.astmetrics;

import java.util.List;
import java.util.Map;

/**
 * @author Mateusz Kutyba, Wroclaw University of Technology
 */
public class AstLogParserData {
    public Map<String, String> tagList;
    public List<String> commitListAll;
    public List<String> commitListBetweenRevisions;

    public AstLogParserData() {
    }
}