package uk.org.tombolo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.tombolo.core.Subject;
import uk.org.tombolo.core.utils.SubjectUtils;
import uk.org.tombolo.execution.spec.DataExportSpecification;
import uk.org.tombolo.execution.spec.DatasourceSpecification;
import uk.org.tombolo.execution.spec.FieldSpecification;
import uk.org.tombolo.execution.spec.SubjectSpecification;
import uk.org.tombolo.exporter.Exporter;
import uk.org.tombolo.field.Field;
import uk.org.tombolo.field.ParentField;
import uk.org.tombolo.field.PredefinedField;
import uk.org.tombolo.importer.DownloadUtils;
import uk.org.tombolo.importer.Importer;
import uk.org.tombolo.importer.ImporterMatcher;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DataExportEngine implements ExecutionEngine{
	private static final Logger log = LoggerFactory.getLogger(DataExportEngine.class);
	private static DownloadUtils downloadUtils;
	private static Properties apiKeys;

	DataExportEngine(Properties apiKeys, DownloadUtils downloadUtils) {
		this.apiKeys = apiKeys;
		this.downloadUtils = downloadUtils;
	}

	public void execute(DataExportSpecification dataExportSpec, Writer writer) throws Exception {
		execute(dataExportSpec, writer, new ImporterMatcher(""));
	}
	
	public void execute(DataExportSpecification dataExportSpec, Writer writer, ImporterMatcher forceImports) throws Exception {
		// Import datasources that are in the global dataset specification
		for (DatasourceSpecification datasourceSpec : dataExportSpec.getDatasetSpecification().getDatasourceSpecification()) {
			importDatasource(forceImports, datasourceSpec);
		}

		// Generate fields
		List<FieldSpecification> fieldSpecs = dataExportSpec.getDatasetSpecification().getFieldSpecification();
		List<Field> fields = new ArrayList<>();
		for (FieldSpecification fieldSpec : fieldSpecs) {
			Field field = fieldSpec.toField();
			fields.add(field);
		}

		prepareFields(fields, forceImports);

		// Use the new fields method
		log.info("Exporting ...");
		List<SubjectSpecification> subjectSpecList = dataExportSpec.getDatasetSpecification().getSubjectSpecification();
		Exporter exporter = (Exporter) Class.forName(dataExportSpec.getExporterClass()).newInstance();
		List<Subject> subjects = SubjectUtils.getSubjectBySpecifications(subjectSpecList);
		exporter.write(writer, subjects, fields);
	}

	private void prepareFields(List<Field> fields, ImporterMatcher forceImports) throws Exception {
		// Import datasources that are specified as part of a predefined field
		for (Field field : fields) {
			if (field instanceof PredefinedField) {
				// This is a predefined field and hence we need to import the appropriate datasources
				for (DatasourceSpecification datasourceSpecification : ((PredefinedField) field).getDatasourceSpecifications()) {
					importDatasource(forceImports, datasourceSpecification);
				}
			}

			if (field instanceof ParentField) {
				// This is a parent field and hence we need to prepare its children
				prepareFields(((ParentField) field).getChildFields(), forceImports);
			}
		}
	}

	private void importDatasource(ImporterMatcher forceImports, DatasourceSpecification datasourceSpec) throws Exception {
		Importer importer = (Importer) Class.forName(datasourceSpec.getImporterClass()).newInstance();
		importer.configure(apiKeys);
		importer.setDownloadUtils(downloadUtils);
		importer.importDatasource(
				datasourceSpec.getDatasourceId(),
				forceImports.doesMatch(datasourceSpec.getImporterClass())
		);
	}
}
