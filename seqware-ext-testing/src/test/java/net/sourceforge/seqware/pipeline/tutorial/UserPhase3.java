/*
 * Copyright (C) 2013 SeqWare
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.seqware.pipeline.tutorial;

import java.io.IOException;
import net.sourceforge.seqware.common.module.ReturnValue;
import net.sourceforge.seqware.pipeline.plugins.ITUtility;
import org.junit.Test;

/**
 *
 * @author dyuen
 */
public class UserPhase3 {
    
    public static final String SAMPLE = "sample";
    
    @Test
    public void createExperimentAndLinkToStudy() throws IOException{
        String output = createSampleAndLinkToExperiment();
        String sw_accession  = String.valueOf(ITUtility.extractSwid(output));
        AccessionMap.accessionMap.put(SAMPLE, sw_accession);
    }

    protected String createSampleAndLinkToExperiment() throws IOException {
        String output = ITUtility.runSeqWareJar(" -p net.sourceforge.seqware.pipeline.plugins.Metadata -- --table sample "
                + "--create --field title::New Test Sample --field description::This is a test description --field experiment_accession::"+AccessionMap.accessionMap.get(UserPhase2.EXPERIMENT) +" --field organism_id::26"
                , ReturnValue.SUCCESS
                , null);
        return output;
    }
}
