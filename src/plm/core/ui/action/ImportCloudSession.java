package plm.core.ui.action;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;

import plm.core.model.Game;
import plm.core.model.session.ZipSessionKit;
import plm.core.utils.FileUtils;

public class ImportCloudSession extends AbstractGameAction {

	private static final long serialVersionUID = -2811343885543779962L;
	private Component parent;

	public ImportCloudSession(Game game, String text, ImageIcon icon,
			Component parent) {
		super(game, text, icon);
		this.parent = parent;
	}

	private void downloadSessionPackage(String fileID, File destFile) throws Exception {
		HttpClient client = null;

		try {
			client = new DefaultHttpClient();
			client.getParams().setParameter(
					CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

			String sessionCloudProviderURL = Game.getProperty(
					Game.SESSION_CLOUD_PROVIDER_URL, "none", false);
			if (sessionCloudProviderURL == null || sessionCloudProviderURL.equals("none"))
				throw new IOException(Game.i18n.tr("No session cloud provider provided (property {0} is {1}). Please update your property file.",
						Game.SESSION_CLOUD_PROVIDER_URL,sessionCloudProviderURL));

			HttpGet get = new HttpGet(sessionCloudProviderURL + "/download/"
					+ fileID);
			HttpResponse res = client.execute(get);

			if (res.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				HttpEntity entity = res.getEntity();
				if (entity != null) {
					BufferedInputStream bis = null;
					BufferedOutputStream bos = null;
					try {
						bis = new BufferedInputStream(entity.getContent());
						bos = new BufferedOutputStream(new FileOutputStream(
								destFile));

						int inByte;
						while ((inByte = bis.read()) != -1) {
							bos.write(inByte);
						}
					} finally {
						if (bos != null)
							bos.close();
						if (bis != null)
							bis.close();
						if (get != null)
							get.abort();
					}
				}
			} else {
				throw new Exception(i18n.tr("Download failed - Please check the code you entered."));
			}
		} finally {
			if (client != null)
				client.getConnectionManager().shutdown();
		}
	}

	private void unzipSessionPackage(File packageFile, File destDir)
			throws IOException {
		final int BUFFER_SIZE = 1024 * 100;

		ZipFile zf = null;
		BufferedOutputStream bos = null;
		InputStream zis = null;
		try {
			zf = new ZipFile(packageFile);

			Enumeration<? extends ZipEntry> entries = zf.entries();

			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String outputFilename = destDir + File.separator
						+ entry.getName();
				int readBytes;
				byte data[] = new byte[BUFFER_SIZE];
				bos = new BufferedOutputStream(new FileOutputStream(
						outputFilename), BUFFER_SIZE);
				zis = zf.getInputStream(entry);
				while ((readBytes = zis.read(data, 0, BUFFER_SIZE)) != -1) {
					bos.write(data, 0, readBytes);
				}
				bos.flush();
			}
		} finally {
			if (zis != null)
				zis.close();
			if (bos != null)
				bos.close();
			if (zf != null)
				zf.close();
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String fileID = JOptionPane
				.showInputDialog(
						this.parent,
						i18n.tr("Please enter your personal code to retrieve your session"),
						i18n.tr("Session code"), JOptionPane.WARNING_MESSAGE);
		if (fileID == null) {
			return;
		}

		File tmpDir = null;
		File tmpFile = null;
		try {
			tmpDir = FileUtils.createTemporaryDirectory();
			tmpFile = FileUtils.createTemporaryFilename();

			downloadSessionPackage(fileID, tmpFile);
			if (! tmpFile.exists()) {
				unzipSessionPackage(tmpFile, tmpDir);

				ZipSessionKit kit = new ZipSessionKit(this.game);
				kit.loadAll(tmpDir);

				JOptionPane.showMessageDialog(this.parent,
					i18n.tr("Your session has been successfully imported."),
					i18n.tr("Importation suceeded"), JOptionPane.INFORMATION_MESSAGE);
			} else {
				throw new Exception(i18n.tr("Something unexpected went wrong during downloading"));
			}

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this.parent,
					i18n.tr("A {0} exception occured while importing your session: {1}",ex.getClass().getName(),ex.getLocalizedMessage()), 
					i18n.tr("Importation error"),
					JOptionPane.ERROR_MESSAGE);
			// TODO: we should log the real exception message in order to be
			// able to debug the failure.
		} finally {
			if (tmpDir != null) {
				tmpDir.delete();
			}
			if (tmpFile != null) {
				tmpFile.delete();
			}
		}
	}

}
