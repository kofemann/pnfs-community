package org.dcache.nfs;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.dcache.nfs.v4.CompoundContext;
import org.dcache.nfs.v4.OperationExecutor;
import org.dcache.nfs.v4.xdr.nfs_argop4;
import org.dcache.nfs.v4.xdr.nfs_opnum4;
import org.dcache.nfs.v4.xdr.nfs_resop4;
import org.dcache.oncrpc4j.rpc.OncRpcException;

import com.codahale.metrics.*;
import com.codahale.metrics.jmx.JmxReporter;

/**
 * An {@link OperationExecutor} decorator that collect request execution
 * statistics.
 */
public class MetricAwareOperationExecutor implements OperationExecutor {

    private final OperationExecutor inner;
    private final MetricRegistry metrics;
    private final JmxReporter reporter;

    public MetricAwareOperationExecutor(OperationExecutor inner) {
        this.inner = inner;
        this.metrics = new MetricRegistry();
        this.reporter = JmxReporter
            .forRegistry(metrics)
            .convertDurationsTo(TimeUnit.MICROSECONDS)
            .convertRatesTo(TimeUnit.SECONDS)
            .inDomain(OperationExecutor.class.getPackageName())
            .build();
    }

    @Override
    public nfs_resop4 execute(CompoundContext context, nfs_argop4 arg) throws IOException, OncRpcException {

        final Timer requests = metrics.timer(nfs_opnum4.toString(arg.argop));
        final Timer.Context time = requests.time();
        try {
            return inner.execute(context, arg);
        } finally {
            time.stop();
        }
    }

    public void start() {
        reporter.start();
    }

    public void shutdown() {
        reporter.stop();
    }

}
