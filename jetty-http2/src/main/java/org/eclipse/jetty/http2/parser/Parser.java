//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http2.parser;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.FrameType;
import org.eclipse.jetty.http2.frames.PriorityFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;

public class Parser
{
    private final HeaderParser headerParser = new HeaderParser();
    private final BodyParser[] bodyParsers = new BodyParser[4];
    private State state = State.HEADER;
    private BodyParser bodyParser;

    public Parser(Listener listener)
    {
        bodyParsers[FrameType.DATA.getType()] = new DataBodyParser(headerParser, listener);
        bodyParsers[FrameType.PRIORITY.getType()] = new PriorityBodyParser(headerParser, listener);
        bodyParsers[FrameType.RST_STREAM.getType()] = new ResetBodyParser(headerParser, listener);
    }

    private void reset()
    {
        state = State.HEADER;
    }

    public boolean parse(ByteBuffer buffer)
    {
        while (buffer.hasRemaining())
        {
            switch (state)
            {
                case HEADER:
                {
                    if (headerParser.parse(buffer))
                    {
                        int type = headerParser.getFrameType();
                        bodyParser = bodyParsers[type];
                        state = State.BODY;
                    }
                    break;
                }
                case BODY:
                {
                    BodyParser.Result result = bodyParser.parse(buffer);
                    if (result == BodyParser.Result.ASYNC)
                    {
                        // The content will be processed asynchronously, stop parsing;
                        // the asynchronous operation will eventually resume parsing.
                        return true;
                    }
                    else if (result == BodyParser.Result.COMPLETE)
                    {
                        reset();
                    }
                    break;
                }
                default:
                {
                    throw new IllegalStateException();
                }
            }
        }
        return false;
    }

    public interface Listener
    {
        public boolean onDataFrame(DataFrame frame);

        public boolean onPriorityFrame(PriorityFrame frame);

        public boolean onResetFrame(ResetFrame frame);

        public static class Adapter implements Listener
        {
            @Override
            public boolean onDataFrame(DataFrame frame)
            {
                return false;
            }

            @Override
            public boolean onPriorityFrame(PriorityFrame frame)
            {
                return false;
            }

            @Override
            public boolean onResetFrame(ResetFrame frame)
            {
                return false;
            }
        }
    }

    private enum State
    {
        HEADER, BODY
    }
}
