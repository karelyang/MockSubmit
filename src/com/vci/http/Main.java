package com.vci.http;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static final String CODE_NAME = "YZM.jpg";
    private static final String ICON_NAME = "images/icon32.png";
    public static AtomicInteger result = new AtomicInteger(0);// 0 ->未提交 1 ->提交成功 -1 ->验证码错误
    private static ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws IOException {
        final MockHttp http = MockHttp.getInstance();
        http.generateCode();

        Display display = new Display();
        final Shell shell = new Shell(display);
        shell.setLayout(new GridLayout(1, false));
        //设置显示图标
        shell.setImage(new Image(display, ICON_NAME));

        Label tip = new Label(shell, SWT.NONE);
        Font boldFont = new Font(display, new FontData(display.getSystemFont().getFontData()[0].getName(), 10, SWT.BOLD));
        tip.setFont(boldFont);
        tip.setText("输入验证码后，点击*开始*按钮\n" +
                "提示：*查询*不需要验证码");

        Composite firstLine = new Composite(shell, SWT.NONE);
        firstLine.setLayout(new GridLayout(3, false));

        Image image = new Image(display, "YZM.jpg");
        final Label imageLabel = new Label(firstLine, SWT.NONE);
        imageLabel.setImage(image);

        new Label(firstLine, SWT.SEPARATOR);
        final Text text = new Text(firstLine, SWT.BORDER);
        text.setBounds(90, 10, 70, 30);
        text.forceFocus();

        Label separator = new Label(shell, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Composite secondLine = new Composite(shell, SWT.FILL);
        secondLine.setLayout(new GridLayout(2, false));

        final Button submit = new Button(secondLine, SWT.PUSH | SWT.CENTER);
        submit.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, SWT.CENTER, false, false));
        submit.setText("开始");
        //Button增加触发事件
        submit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent paramSelectionEvent) {
                MessageBox dialog = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
                dialog.setText("提示信息");

                String code = text.getText();
                if (code == null || code.trim().equals("")) {
                    text.forceFocus();
                    dialog.setMessage("请输入验证码");
                } else {
                    submit.setEnabled(false);
                    imageLabel.setEnabled(false);

                    if (execute(http, code)) {
                        submit.setEnabled(true);

                        if (result.get() == 1)
                            dialog.setMessage("程序执行完毕，在Excel中查看结果");
                        else {
                            result.set(0);
                            dialog.setMessage("验证码错误！");
                        }
                        imageLabel.setEnabled(true);
                    }
                }
                dialog.open();
            }
        });

        final Button check = new Button(secondLine, SWT.PUSH);
        check.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END, SWT.CENTER, false, false));
        check.setText("查询");
        check.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent paramSelectionEvent) {
                MessageBox dialog = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
                dialog.setText("提示信息");

                submit.setEnabled(false);
                check.setEnabled(false);
                imageLabel.setEnabled(false);

                try {
                    Map<Integer, String[]> persons = RWExcelFile.cache_data.size() > 0 ? RWExcelFile.cache_data : RWExcelFile.readFile();

                    Map<Integer, String> alerts = http.check(persons);
                    RWExcelFile.writeResult(alerts, 7);
                    submit.setEnabled(true);
                    check.setEnabled(true);
                    imageLabel.setEnabled(true);
                    dialog.setMessage("查询完毕");
                    dialog.open();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //验证码增加点击事件
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent event) {
                try {
                    http.generateCode();
                    Image paramImage = new Image(imageLabel.getDisplay(), CODE_NAME);
                    imageLabel.setImage(paramImage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        shell.addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent shellEvent) {
                new File("YZM.jpg").deleteOnExit();
                try {
                    http.closeClient();
                    result.set(1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        shell.setText("报名程序");
        shell.pack();

        //居中显示
        Rectangle bounds = Display.getDefault().getPrimaryMonitor().getBounds();
        Rectangle rect = shell.getBounds();
        int x = bounds.x + (bounds.width - rect.width) / 2;
        int y = bounds.y + (bounds.height - rect.height) / 2;
        shell.setLocation(x, y);

        shell.open();
        shell.moveAbove(Display.getCurrent().getActiveShell());

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }

    private static boolean execute(MockHttp http, String code) {
        try {
            service.scheduleAtFixedRate(() -> {
                try {
                    Map<Integer, String[]> persons = RWExcelFile.cache_data.size() > 0 ? RWExcelFile.cache_data : RWExcelFile.readFile();

                    http.post(code, persons);
                    if (MockHttp.cache_result.remove(0, "验证码错！")) {
                        result.set(-1);
                    } else if (MockHttp.cache_result.size() == persons.size()) {
                        RWExcelFile.writeResult(MockHttp.cache_result, 6);
                        service.shutdown();
                        result.set(1);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, 0, 5, TimeUnit.MINUTES);
            while (true) {
                if (result.get() != 0)
                    return true;
                Thread.sleep(10 * 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}