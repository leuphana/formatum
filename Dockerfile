FROM pandoc/latex:latest AS pandoc
RUN tlmgr update --self && tlmgr install times

FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /formatum/
RUN chown 1001 /formatum \
    && chmod "g+rwX" /formatum \
    && chown 1001:root /formatum
COPY --chown=1001:root --chmod=0755 target/*-runner /formatum/application

COPY --from=pandoc /usr/lib/lua5.4/liblua-5.4.so.0 /usr/lib/lua5.4/liblua-5.4.so.0
COPY --from=pandoc /usr/lib/liblua-5.4.so.0 /usr/lib/liblua-5.4.so.0
COPY --from=pandoc /lib/libc.musl-x86_64.so.1 /lib/libc.musl-x86_64.so.1
COPY --from=pandoc /lib/ld-musl-x86_64.so.1 /lib/ld-musl-x86_64.so.1
COPY --from=pandoc /usr/lib/libz.so.1 /usr/lib/libz.so.1
COPY --from=pandoc /usr/lib/libgmp.so.10 /usr/lib/libgmp.so.10
COPY --from=pandoc /usr/lib/libffi.so.8 /usr/lib/libffi.so.8
COPY --from=pandoc /opt/texlive/ /opt/texlive/
COPY --from=pandoc /usr/local/bin/pandoc /usr/local/bin/pandoc
COPY --from=pandoc /usr/local/bin/pandoc-crossref /usr/local/bin/pandoc-crossref
RUN ln -sf /usr/local/bin/pandoc /usr/local/bin/pandoc-lua
RUN ln -sf /usr/local/bin/pandoc /usr/local/bin/pandoc-server
RUN ln -s /opt/texlive/texdir/bin/x86_64-linuxmusl/pdflatex /usr/local/bin/pdflatex

EXPOSE 8181
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8181"]
